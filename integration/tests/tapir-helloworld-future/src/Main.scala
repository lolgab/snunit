package snunit.tests

import snunit.tapir.SNUnitInterpreterFuture._
import sttp.tapir._

import scala.concurrent.Future

object TapirHelloWorldFuture {
  val helloWorld = endpoint.get
    .in("hello")
    .in(query[String]("name"))
    .out(stringBody)
    .serverLogic[Future](name => Future.successful(Right(s"Hello $name!")))

  def main(args: Array[String]): Unit =
    snunit.AsyncServerBuilder
      .build(toHandler(helloWorld :: Nil))
}
