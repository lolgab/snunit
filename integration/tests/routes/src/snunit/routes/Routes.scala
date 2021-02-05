package snunit.tests

import snunit._
import snunit.routes._
import trail._

object Routes {
  def main(args: Array[String]): Unit = {
    val server: SyncServer =
      SyncServerBuilder()
        .withRequestHandler(
          _.withMethod(Method.GET)
            .withRoute(Root / "test" / Arg[Int]) { (req, i) =>
              req.send(
                statusCode = StatusCode.OK,
                content = s"Got $i",
                headers = Seq("Content-Type" -> "text/plain")
              )
            }
        )
        .withRequestHandler(req =>
          req.send(
            statusCode = StatusCode.NotFound,
            content = "Not found",
            headers = Seq("Content-Type" -> "text/plain")
          )
        )
        .build()

    server.listen()
  }
}
