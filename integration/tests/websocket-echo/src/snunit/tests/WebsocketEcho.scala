package snunit.tests

import snunit.*

object MyRequestHandler extends RequestHandler {
  def handleRequest(req: Request): Unit = {
    if (req.isWebsocketHandshake) {
      req.upgrade()
    } else {
      req.send(StatusCode.NotFound, Array.emptyByteArray, Headers.empty)
    }
  }
}

object MyWebsocketHandler extends WebsocketHandler {
  def handleFrame(frame: Frame): Unit = {
    if (frame.opcode == Opcode.Pong.value) {
      frame.sendFrameDone()
    } else {
      frame.sendFrame(frame.opcode, frame.fin, frame.frameContentRaw())
      frame.sendFrameDone()
      if (frame.opcode == Opcode.Close.value) {
        frame.frameRequest.sendDone()
      }
    }
  }
}

object HelloWorld {
  def main(args: Array[String]): Unit = {
    SyncServerBuilder
      .setRequestHandler(MyRequestHandler)
      .setWebsocketHandler(MyWebsocketHandler)
      .build()
      .listen()
  }
}
