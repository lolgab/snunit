package snunit

import snunit._
import trail._

object routes {
  implicit class RequestOps(private val req: Request) extends AnyVal {
    def withRoute[Args](route: Route[Args])(f: (Request, Args) => Unit): Unit =
      req.withFilter {
        val res = route.parseArgs(req.path)
        res match {
          case Some(args) => f(req, args)
          case None       => req.next()
        }
      }
  }
}
