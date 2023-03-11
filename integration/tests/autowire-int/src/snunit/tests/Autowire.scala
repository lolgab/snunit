package snunit.tests

import snunit.AsyncServerBuilder
import snunit.Autowire._
import snunit.StatusCode

import scala.concurrent.ExecutionContext.Implicits.global

trait MyApi {
  def addOne(i: Int): Int
}

object MyApiImpl extends MyApi {
  def addOne(i: Int): Int = i + 1
}

object Autowire {
  def main(args: Array[String]): Unit = {
    val fallback: snunit.RequestHandler = _.send(StatusCode.NotFound, "Not found", Seq.empty)
    AsyncServerBuilder
      .setRequestHandler(new AutowireHandler(UpickleAutowireServer.route[MyApi](MyApiImpl), fallback))
      .build()
  }
}
