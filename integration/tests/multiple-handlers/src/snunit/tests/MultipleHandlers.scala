package snunit.tests

import snunit.*

object MultipleHandlers {
  def main(args: Array[String]): Unit = {
    val fallback: RequestHandler = _.send(
      statusCode = StatusCode.NotFound,
      content = s"Not found\n",
      headers = Headers("Content-Type" -> "text/plain")
    )

    val server = SyncServerBuilder
      .setRequestHandler(req => {
        if (req.method == Method.GET) {
          req.send(
            statusCode = StatusCode.OK,
            content = s"Hello world multiple handlers!\n",
            headers = Headers("Content-Type" -> "text/plain")
          )
        } else fallback.handleRequest(req)
      })
      .build()

    server.listen()
  }
}
