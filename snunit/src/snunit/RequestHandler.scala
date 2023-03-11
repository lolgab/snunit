package snunit

trait RequestHandler {
  def handleRequest(req: Request): Unit
}
