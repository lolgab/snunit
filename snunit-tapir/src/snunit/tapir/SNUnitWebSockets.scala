package snunit.tapir

import snunit.*
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}
import sttp.ws.WebSocketFrame

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8

/** Adapts the raw frames of [[snunit.WebsocketConnections]] to the sttp websocket frames. */
private[tapir] object SNUnitWebSockets extends snunit.WebsocketHandler {

  /** Receives the frames of a single connection, already reassembled. `None` means that the client closed. */
  private final class Connection(concatenateFragments: Boolean, offer: Option[WebSocketFrame] => Unit) {
    private var partialOpcode: Byte = Opcode.Text.value
    private val partial = new ByteArrayOutputStream()

    private def data(opcode: Byte, last: Boolean, bytes: Array[Byte]): WebSocketFrame =
      if (opcode == Opcode.Text.value) WebSocketFrame.Text(new String(bytes, UTF_8), last, None)
      else WebSocketFrame.Binary(bytes, last, None)

    def onFrame(opcode: Byte, last: Boolean, bytes: Array[Byte]): Unit = {
      if (opcode == Opcode.Ping.value) offer(Some(WebSocketFrame.Ping(bytes)))
      else if (opcode == Opcode.Pong.value) offer(Some(WebSocketFrame.Pong(bytes)))
      else if (opcode == Opcode.Close.value) {
        val frame =
          if (bytes.length >= 2)
            WebSocketFrame.Close(
              ((bytes(0) & 0xff) << 8) | (bytes(1) & 0xff),
              new String(bytes, 2, bytes.length - 2, UTF_8)
            )
          else WebSocketFrame.Close(1005, "")
        offer(Some(frame))
        offer(None)
      } else {
        // Text, Binary or Continuation
        if (opcode != Opcode.Cont.value) partialOpcode = opcode
        if (concatenateFragments) {
          partial.write(bytes)
          if (last) {
            val all = partial.toByteArray
            partial.reset()
            offer(Some(data(partialOpcode, true, all)))
          }
        } else offer(Some(data(partialOpcode, last, bytes)))
      }
    }
  }

  def register(req: Request, concatenateFragments: Boolean, offer: Option[WebSocketFrame] => Unit): Unit = {
    val connection = new Connection(concatenateFragments, offer)
    WebsocketConnections.register(req, connection.onFrame)
  }

  def unregister(req: Request): Unit = WebsocketConnections.unregister(req)

  override def handleFrame(frame: Frame): Unit = WebsocketConnections.handleFrame(frame)

  /** Sends frames to the client. After a close frame, everything else is ignored. */
  final class Sender(req: Request) {
    private var closeSent = false
    private var continuing = false

    def send(frame: WebSocketFrame): Unit = if (!closeSent) {
      frame match {
        case WebSocketFrame.Text(payload, last, _) =>
          req.sendWebsocketFrame(
            if (continuing) Opcode.Cont.value else Opcode.Text.value,
            if (last) 1 else 0,
            payload.getBytes(UTF_8)
          )
          continuing = !last
        case WebSocketFrame.Binary(payload, last, _) =>
          req.sendWebsocketFrame(
            if (continuing) Opcode.Cont.value else Opcode.Binary.value,
            if (last) 1 else 0,
            payload
          )
          continuing = !last
        case WebSocketFrame.Ping(payload) => req.sendWebsocketFrame(Opcode.Ping.value, 1, payload)
        case WebSocketFrame.Pong(payload) => req.sendWebsocketFrame(Opcode.Pong.value, 1, payload)
        case WebSocketFrame.Close(code, reason) =>
          closeSent = true
          val reasonBytes = reason.getBytes(UTF_8)
          val bytes = new Array[Byte](2 + reasonBytes.length)
          bytes(0) = (code >> 8).toByte
          bytes(1) = code.toByte
          System.arraycopy(reasonBytes, 0, bytes, 2, reasonBytes.length)
          req.sendWebsocketFrame(Opcode.Close.value, 1, bytes)
      }
    }
  }

  def decode[REQ](o: WebSocketBodyOutput[?, REQ, ?, ?, ?], frame: WebSocketFrame): REQ =
    o.requests.decode(frame) match {
      case DecodeResult.Value(v) => v
      case failure               => throw new IllegalArgumentException(s"Cannot decode $frame: $failure")
    }

  /** Registers the connection of `req` and runs a synchronous pipe over its frames. It can be called before the
    * upgrade.
    */
  def runSync[REQ, RESP](
      req: Request,
      pipe: REQ => Iterable[RESP],
      o: WebSocketBodyOutput[?, REQ, RESP, ?, ?]
  ): Unit = {
    val sender = new Sender(req)
    var finished = false
    def finish(): Unit = if (!finished) {
      finished = true
      try sender.send(WebSocketFrame.Close(1000, ""))
      finally unregister(req)
    }
    def process(frame: WebSocketFrame): Unit =
      pipe(decode(o, frame)).foreach { resp =>
        if (!finished) {
          val encoded = o.responses.encode(resp)
          sender.send(encoded)
          encoded match {
            case _: WebSocketFrame.Close => finish()
            case _                       =>
          }
        }
      }
    register(
      req,
      o.concatenateFragmentedFrames,
      {
        case None => finish()
        case Some(frame) =>
          if (!finished) try {
            frame match {
              case WebSocketFrame.Ping(payload) =>
                if (o.autoPongOnPing) sender.send(WebSocketFrame.Pong(payload)) else process(frame)
              case _: WebSocketFrame.Pong  => if (!o.ignorePong) process(frame)
              case _: WebSocketFrame.Close => if (o.decodeCloseRequests) process(frame)
              case _                       => process(frame)
            }
          } catch {
            case e: Exception =>
              System.err.println("Error while processing the websocket")
              e.printStackTrace()
              finished = true
              try sender.send(WebSocketFrame.Close(1011, "Internal error"))
              finally unregister(req)
          }
      }
    )
  }
}
