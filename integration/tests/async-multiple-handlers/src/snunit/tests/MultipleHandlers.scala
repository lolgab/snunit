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
                statusCode = 200,
                content = s"Hello world async multiple handlers!\n",
                headers = Seq("Content-Type" -> "text/plain")
              )
            } else req.next()
          }
        })
      )
      .withRequestHandler(req => {
        req.send(
          statusCode = 404,
          content = s"Not found\n",
          headers = Seq("Content-Type" -> "text/plain")
        )
      })
      .build()
  }
}
