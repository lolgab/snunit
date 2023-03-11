package snunit.tests

import snunit.AsyncServerBuilder
import snunit.Autowire._
import snunit.StatusCode

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

object Autowire {
  def main(args: Array[String]): Unit = {
    val fallbackHandler: snunit.RequestHandler = r => r.send(StatusCode.NotFound, "Not found", Seq.empty)
    val autowireServer = UpickleAutowireServer.route[MyApi](MyApiImpl)
    val handler = new AutowireHandler(autowireServer, fallbackHandler)

    AsyncServerBuilder.setRequestHandler(handler).build()
  }
}
