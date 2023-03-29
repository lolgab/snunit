package snunit.tests

import snunit.*

import scala.concurrent.ExecutionContext.Implicits.global

object AsyncMultipleHandlers {
  val fallback: RequestHandler = _.send(
    statusCode = StatusCode.NotFound,
    content = s"Not found\n",
    headers = Headers("Content-Type" -> "text/plain")
  )
  def main(args: Array[String]): Unit = {
    AsyncServerBuilder
      .setRequestHandler(req =>
        global.execute(() => {
          if (req.method == Method.GET) {
            req.send(
              statusCode = StatusCode.OK,
              content = s"Hello world async multiple handlers!\n",
              headers = Headers("Content-Type" -> "text/plain")
            )
          } else fallback.handleRequest(req)
        })
      )
      .build()
  }
}
