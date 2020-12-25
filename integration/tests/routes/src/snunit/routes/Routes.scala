package snunit.tests

import snunit._
import snunit.routes._
import trail._

object Routes {
  def main(args: Array[String]): Unit = {
    val server =
      SyncServerBuilder()
        .withRoute(Root / "test" / Arg[Int]) { case (req, i) =>
          req.send(
            statusCode = StatusCode.OK,
            content = s"Got $i",
            headers = Seq("Content-Type" -> "text/plain")
          )
        }
        .withRequestHandler(req =>
          req.send(
            statusCode = StatusCode.NotFound,
            content = s"Not found",
            headers = Seq("Content-Type" -> "text/plain")
          )
        )
        .build()

    server.listen()
  }
}
