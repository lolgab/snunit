package snunit.tests

import snunit._

object MultipleHandlers {
  def main(args: Array[String]): Unit = {
    val fallback: Handler = _.send(
      statusCode = StatusCode.NotFound,
      content = s"Not found\n",
      headers = Seq("Content-Type" -> "text/plain")
    )

    val server = SyncServerBuilder.build(req => {
      if (req.method == Method.GET) {
        req.send(
          statusCode = StatusCode.OK,
          content = s"Hello world multiple handlers!\n",
          headers = Seq("Content-Type" -> "text/plain")
        )
      } else fallback.handleRequest(req)
    })

    server.listen()
  }
}
