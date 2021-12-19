package snunit.routes

import snunit._
import trail._

trait ArgsHandler[Args] {
  def handleRequest(req: Request, args: Args): Unit
}

class RouteHandler[Args](route: Route[Args], argsHandler: ArgsHandler[Args], next: Handler) extends Handler {
  def handleRequest(req: Request): Unit = {
    val res = route.parseArgs(req.path)
    res match {
      case Some(args) => argsHandler.handleRequest(req, args)
      case None       => next.handleRequest(req)
    }
  }
}
object RouteHandler {
  @inline def apply[Args](route: Route[Args], argsHandler: ArgsHandler[Args], next: Handler): RouteHandler[Args] =
    new RouteHandler[Args](route = route, argsHandler = argsHandler, next = next)
}
