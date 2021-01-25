package snunit.tests

import scala.concurrent.ExecutionContext.Implicits.global

import snunit.AsyncServerBuilder
import snunit.Autowire._
import snunit.StatusCode

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
      .withRequestHandler { r => r.send(StatusCode.NotFound, "Not found", Seq.empty) }
      .build()
  }
}
