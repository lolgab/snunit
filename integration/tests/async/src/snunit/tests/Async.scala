package snunit.tests

import snunit.*

import scala.concurrent.duration._
import scala.scalanative.loop.Timer

object Async {
  def main(args: Array[String]): Unit = {
    AsyncServerBuilder
      .setRequestHandler(req => {
        req.method match {
          case Method.GET =>
            var t: Timer = null.asInstanceOf[Timer]
            t = Timer.timeout(500.millis) { () =>
              req.send(
                statusCode = StatusCode.OK,
                content = "Hello world async!\n",
                headers = Headers("Content-Type" -> "text/plain")
              )
              t.clear()
            }
          case _ =>
            req.send(
              statusCode = StatusCode.NotFound,
              content = "Not found\n",
              headers = Headers("Content-Type" -> "text/plain")
            )
        }
      })
      .build()
  }
}
