package snunit

trait Handler {
  def handleRequest(req: Request): Unit
}
