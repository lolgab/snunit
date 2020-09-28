package snunit.tests

import snunit._

object MultipleHandlers {
  def main(args: Array[String]): Unit = {
    val server =
      SyncServerBuilder()
        .withRequestHandler(req => {
          if (req.method == Method.GET) {
            req.send(
              statusCode = 200,
              content = s"Hello world multiple handlers!\n",
              headers = Seq("Content-Type" -> "text/plain")
            )
          } else req.next()
        })
        .withRequestHandler(req => {
          req.send(
            statusCode = 404,
            content = s"Not found\n",
            headers = Seq("Content-Type" -> "text/plain")
          )
        })
        .build()

    server.listen()
  }
}
