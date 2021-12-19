package snunit.tests

import snunit._
import snunit.routes._
import trail._

object Routes {
  def main(args: Array[String]): Unit = {
    val fallback: Handler = _.send(
      statusCode = StatusCode.NotFound,
      content = "Not found",
      headers = Seq("Content-Type" -> "text/plain")
    )
    val argsHandler: ArgsHandler[Int] = (req, i) =>
      req.send(
        statusCode = StatusCode.OK,
        content = s"Got $i",
        headers = Seq("Content-Type" -> "text/plain")
      )
    val server: SyncServer = SyncServerBuilder.build(
      MethodHandler(Method.GET, RouteHandler(Root / "test" / Arg[Int], argsHandler, fallback), fallback)
    )

    server.listen()
  }
}
