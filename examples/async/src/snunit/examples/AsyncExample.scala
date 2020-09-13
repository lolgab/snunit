package snunit.examples

import snunit._
import scala.scalanative.loop.Timer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object AsyncExample {
  def main(args: Array[String]): Unit = {
    AsyncServerBuilder()
      .withRequestHandler(req => {
        req.method match {
          case Method.GET =>
            var t: Timer = null.asInstanceOf[Timer]
            t = Timer.timeout(500) { () =>
              req.send(
                statusCode = 200,
                content = s"Hello world async!\n",
                headers = Seq("Content-Type" -> "text/plain")
              )
              t.clear()
            }
            true
          case _ =>
            req.send(
              statusCode = 404,
              content = s"Not found\n",
              headers = Seq("Content-Type" -> "text/plain")
            )
        }
      })
      .build()
  }
}
