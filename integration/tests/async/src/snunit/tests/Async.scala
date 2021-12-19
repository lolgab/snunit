package snunit.tests

import scala.concurrent.duration._
import scala.scalanative.loop.Timer

import snunit._

object Async {
  def main(args: Array[String]): Unit = {
    AsyncServerBuilder
      .build(req => {
        req.method match {
          case Method.GET =>
            var t: Timer = null.asInstanceOf[Timer]
            t = Timer.timeout(500.millis) { () =>
              req.send(
                statusCode = StatusCode.OK,
                content = s"Hello world async!\n",
                headers = Seq("Content-Type" -> "text/plain")
              )
              t.clear()
            }
          case _ =>
            req.send(
              statusCode = StatusCode.NotFound,
              content = s"Not found\n",
              headers = Seq("Content-Type" -> "text/plain")
            )
        }
      })
  }
}
