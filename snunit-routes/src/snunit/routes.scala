package snunit

import snunit._
import trail._

object routes {
  implicit class ServerBuilderOps[T <: ServerBuilder](private val builder: T) extends AnyVal {
    def withRoute[Args](route: Route[Args])(handler: (Request, Args) => Unit): T = {
      builder.withRequestHandler(req =>
        route.unapply(req.path) match {
          case Some(args) => handler(req, args)
          case None       => req.next()
        }
      )
    }
  }
}
