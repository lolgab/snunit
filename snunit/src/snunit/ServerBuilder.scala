package snunit

trait ServerBuilder {
  def withWebsocketHandler(handler: WSFrame => Unit): ServerBuilder
  def withRequestHandler(handler: Request => Unit): ServerBuilder
  def build(): Server
}
