package snunit.examples

import snunit.snautowire._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MyApi {
  def helloAsync(name: String): Future[String]
  def helloSync(name: String): String
}

object MyApiImpl extends MyApi {
  def helloAsync(name: String): Future[String] = Future.successful(s"Hello $name")
  def helloSync(name: String): String = s"Hello $name"
}

object AutowireExample {
  def main(args: Array[String]): Unit = {
    val server = snautowire.createServer(UpickleAutowireServer.route[MyApi](MyApiImpl))

    while (true) {
      scala.scalanative.runtime.loop()
      server.runOnce()
    }
  }
}
