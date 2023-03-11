package snunit

trait WebsocketHandler {
  def handleFrame(frame: Frame): Unit
}
