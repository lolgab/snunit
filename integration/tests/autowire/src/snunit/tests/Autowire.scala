package snunit.tests

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import snunit.AsyncServerBuilder
import snunit.Autowire._
import snunit.StatusCode

trait MyApi {
  def helloAsync(name: String): Future[String]
  def helloSync(name: String): String
}

object MyApiImpl extends MyApi {
  def helloAsync(name: String): Future[String] = Future(s"Hello $name")
  def helloSync(name: String): String = s"Hello $name"
}

object Autowire {
  def main(args: Array[String]): Unit = {
    AsyncServerBuilder()
      .withAutowireRouter(UpickleAutowireServer.route[MyApi](MyApiImpl))
      .withRequestHandler { r => r.send(StatusCode.NotFound, "Not found", Seq.empty) }
      .build()
  }
}
