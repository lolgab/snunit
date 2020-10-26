package snunit.tests

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import snunit.AsyncServerBuilder
import snunit.Autowire._

trait MyApi {
  def addOne(i: Int): Int
}

object MyApiImpl extends MyApi {
  def addOne(i: Int): Int = i + 1
}

object Autowire {
  def main(args: Array[String]): Unit = {
    AsyncServerBuilder()
      .withAutowireRouter(UpickleAutowireServer.route[MyApi](MyApiImpl))
      .withRequestHandler { r => r.send(404, "Not found", Seq.empty) }
      .build()
  }
}
