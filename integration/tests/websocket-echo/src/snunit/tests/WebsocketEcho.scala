import snunit._

object MyRequestHandler extends RequestHandler {
  def handleRequest(req: Request): Unit = {
    if (req.isWebsocketHandshake) {
      req.upgrade()
    } else {
      req.send(StatusCode.NotFound, Array.emptyByteArray, Seq.empty[(String, String)])
    }
  }
}

object MyWebsocketHandler extends WebsocketHandler {
  def handleFrame(frame: Frame): Unit = {
    if (frame.opcode == 9 /* PONG */ ) {
      frame.sendFrameDone()
    } else {
      frame.sendFrame(frame.opcode, frame.fin, frame.frameContentRaw())
      frame.sendFrameDone()
      if (frame.opcode == 8 /* CLOSE */ ) {
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
