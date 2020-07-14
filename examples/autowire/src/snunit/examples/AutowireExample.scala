package snunit.examples

import snunit.snautowire._
import snunit.AsyncServerBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MyApi {
  def helloAsync(name: String): Future[String]
  def helloSync(name: String): String
}

object MyApiImpl extends MyApi {
  def helloAsync(name: String): Future[String] = Future(s"Hello $name")
  def helloSync(name: String): String = s"Hello $name"
}

object AutowireExample {
  def main(args: Array[String]): Unit = {
    snautowire.createServer(AsyncServerBuilder, UpickleAutowireServer.route[MyApi](MyApiImpl))
  }
}
