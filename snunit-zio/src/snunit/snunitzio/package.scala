package snunit

import scala.annotation.nowarn

import zio._

package object snunitzio {
  implicit class ServerBuilderZioOps(private val builder: AsyncServerBuilder) extends AnyVal {
    @nowarn
    def withZIORequestHandler(handler: ZIORequest => URIO[ZEnv, Response]): AsyncServerBuilder = {
      builder.withRequestHandler { req =>
        Runtime.global.unsafeRunAsync(handler(new ZIORequest(req))) {
          case Exit.Success(Response(code, body, headers)) =>
            req.send(code, body, headers)
        }
      }
    }
  }
}
