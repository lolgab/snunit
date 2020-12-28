package snunit.tests

import snunit._

object HandlersComposition {
  def main(args: Array[String]): Unit = {
    val server =
      SyncServerBuilder()
        .withRequestHandler(
          _.withMethod(Method.GET)
            .withPath("/") { req =>
              req.send(
                statusCode = StatusCode.OK,
                content = s"Hello world!\n",
                headers = Seq("Content-Type" -> "text/plain")
              )
            }
        )
        .withRequestHandler { req =>
          req.send(
            statusCode = StatusCode.NotFound,
            content = s"Not found!\n",
            headers = Seq("Content-Type" -> "text/plain")
          )
        }
        .build()

    server.listen()
  }
}
