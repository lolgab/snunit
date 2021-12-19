package snunit.tests

import scala.concurrent.ExecutionContext.Implicits.global

import snunit._

object AsyncMultipleHandlers {
  val fallback: Handler = _.send(
    statusCode = StatusCode.NotFound,
    content = s"Not found\n",
    headers = Seq("Content-Type" -> "text/plain")
  )
  def main(args: Array[String]): Unit = {
    AsyncServerBuilder.build(req =>
      global.execute(() => {
        if (req.method == Method.GET) {
          req.send(
            statusCode = StatusCode.OK,
            content = s"Hello world async multiple handlers!\n",
            headers = Seq("Content-Type" -> "text/plain")
          )
        } else fallback.handleRequest(req)
      })
    )
  }
}
