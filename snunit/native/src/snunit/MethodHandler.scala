package snunit

class MethodHandler(method: Method, handler: Handler, next: Handler) extends Handler {
  def handleRequest(req: Request): Unit = {
    if (req.method == method) handler.handleRequest(req)
    else next.handleRequest(req)
  }
}
object MethodHandler {
  @inline def apply(method: Method, handler: Handler, next: Handler): MethodHandler =
    new MethodHandler(method = method, handler = handler, next = next)
}
