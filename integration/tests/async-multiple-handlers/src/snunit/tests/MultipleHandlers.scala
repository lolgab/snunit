package snunit.tests

import scala.concurrent.ExecutionContext.Implicits.global

import snunit._

object MultipleHandlers {
  def main(args: Array[String]): Unit = {
    AsyncServerBuilder()
      .withRequestHandler(req =>
        global.execute(new Runnable {
          def run(): Unit = {
            if (req.method == Method.GET) {
              req.send(
                statusCode = StatusCode.OK,
                content = s"Hello world async multiple handlers!\n",
                headers = Seq("Content-Type" -> "text/plain")
              )
            } else req.next()
          }
        })
      )
      .withRequestHandler(req => {
        req.send(
          statusCode = StatusCode.NotFound,
          content = s"Not found\n",
          headers = Seq("Content-Type" -> "text/plain")
        )
      })
      .build()
  }
}
