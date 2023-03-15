import snunit._

object MyRequestHandler extends RequestHandler {
  def handleRequest(req: Request): Unit = {
    if (req.isWebsocketHandshake) {
      req.upgrade()
    } else {
      req.send(404, Array.emptyByteArray, Seq.empty)
    }
  }
}

object MyWebsocketHandler extends WebsocketHandler {
  def handleFrame(frame: Frame): Unit = {
    if (frame.opcode == 9 /* PONG */ ) {
      frame.sendDone()
    } else {
      frame.send(frame.opcode, frame.fin, frame.contentRaw)
      frame.sendDone()
      if (frame.opcode == 8 /* CLOSE */ ) {
        frame.request.sendDone()
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
