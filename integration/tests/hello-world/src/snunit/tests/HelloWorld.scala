package snunit.tests

import snunit._

object HelloWorld {
  def main(args: Array[String]): Unit = {
    val server =
      SyncServerBuilder()
        .withRequestHandler(req => {
          req.method match {
            case Method.GET =>
              req.send(
                statusCode = StatusCode.OK,
                content = s"Hello world!\n",
                headers = Seq("Content-Type" -> "text/plain")
              )
            case _ =>
              req.send(
                statusCode = StatusCode.NotFound,
                content = s"Not found\n",
                headers = Seq("Content-Type" -> "text/plain")
              )
          }
        })
        .build()

    server.listen()
  }
}
