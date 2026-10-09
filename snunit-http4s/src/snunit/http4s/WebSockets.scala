package snunit.http4s

import cats.effect.*
import cats.effect.implicits.*
import cats.effect.std.{Dispatcher, Queue}
import cats.syntax.all.*
import fs2.Stream
import org.http4s.websocket.WebSocketFrame
import scodec.bits.ByteVector
import snunit.*

private[http4s] object WebSockets {

  private def toHttp4s(opcode: Byte, last: Boolean, bytes: Array[Byte]): WebSocketFrame = {
    val data = ByteVector.view(bytes)
    if (opcode == Opcode.Text.value) WebSocketFrame.Text(data, last)
    else if (opcode == Opcode.Binary.value) WebSocketFrame.Binary(data, last)
    else if (opcode == Opcode.Cont.value) WebSocketFrame.Continuation(data, last)
    else if (opcode == Opcode.Ping.value) WebSocketFrame.Ping(data)
    else if (opcode == Opcode.Pong.value) WebSocketFrame.Pong(data)
    else WebSocketFrame.Close(data)
  }

  private def send(req: Request, frame: WebSocketFrame): Unit = {
    val opcode = frame match {
      case _: WebSocketFrame.Text         => Opcode.Text.value
      case _: WebSocketFrame.Binary       => Opcode.Binary.value
      case _: WebSocketFrame.Continuation => Opcode.Cont.value
      case _: WebSocketFrame.Ping         => Opcode.Ping.value
      case _: WebSocketFrame.Pong         => Opcode.Pong.value
      case _: WebSocketFrame.Close        => Opcode.Close.value
      case other                          => other.opcode.toByte
    }
    req.sendWebsocketFrame(opcode, if (frame.last) 1 else 0, frame.data.toArray)
  }

  /** Registers the connection of `req` and returns the action which runs `pipe` over its frames, to be executed once
    * `req` is upgraded.
    */
  def prepare[F[_]: Async](
      dispatcher: Dispatcher[F],
      req: Request,
      pipe: fs2.Pipe[F, WebSocketFrame, WebSocketFrame],
      onClose: F[Unit]
  ): F[F[Unit]] = {
    val F = Async[F]
    var closeSent = false
    def sendFrame(frame: WebSocketFrame): F[Unit] = F.delay {
      if (!closeSent) {
        if (frame.isInstanceOf[WebSocketFrame.Close]) closeSent = true
        send(req, frame)
      }
    }

    Queue.unbounded[F, Option[WebSocketFrame]].flatMap { queue =>
      val incoming: Stream[F, WebSocketFrame] = Stream
        .fromQueueNoneTerminated(queue)
        .evalTap {
          // the pipe doesn't see pings if `filterPingPongs` is set, so they are always answered here
          case WebSocketFrame.Ping(data) => sendFrame(WebSocketFrame.Pong(data))
          case _                         => F.unit
        }

      val outgoing = pipe(incoming).takeThrough {
        case _: WebSocketFrame.Close => false
        case _                       => true
      }

      // frames must be enqueued in order, which the parallel dispatcher doesn't guarantee
      val register = Dispatcher.sequential[F].allocated.flatMap { case (sequential, release) =>
        F.delay {
          WebsocketConnections.register(
            req,
            (opcode, last, bytes) => {
              val frame = toHttp4s(opcode, last, bytes)
              sequential.unsafeRunAndForget(queue.offer(Some(frame)) *> {
                if (frame.isInstanceOf[WebSocketFrame.Close]) queue.offer(None) else F.unit
              })
            }
          )
        }.as(release)
      }

      val run = outgoing
        .evalMap(sendFrame)
        .compile
        .drain
        .handleErrorWith { e =>
          F.delay {
            System.err.println("Error while processing the websocket")
            e.printStackTrace()
          }
        }
        .guarantee(sendFrame(WebSocketFrame.Close(ByteVector(0x03, 0xe8.toByte))).attempt.void)
        .guarantee(onClose.attempt.void)
        .guarantee(F.delay(WebsocketConnections.unregister(req)))

      // The pipe is run in the background: the request handler is done once the connection is upgraded
      register.map(release => F.delay(dispatcher.unsafeRunAndForget(run.guarantee(release))))
    }
  }
}
