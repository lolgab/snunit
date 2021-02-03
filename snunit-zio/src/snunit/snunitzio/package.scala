package snunit

import zio._

package object snunitzio {

  implicit class ServerBuilderZioOps(private val builder: AsyncServerBuilder) extends AnyVal {
    def withZIORequestHandler(handler: Request => ZIO[Any, Nothing, Response]): AsyncServerBuilder = {
      builder
        .withRequestHandler(req => {
          Runtime.default.unsafeRunAsync(handler(req)) {
            case Exit.Success(Response(code, body, headers)) =>
              req.send(code, body, headers)
            case Exit.Failure(nothing) =>
          }
        })
    }
  }
}
