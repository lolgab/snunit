package snunit.tests

import snunit.tapir.SNUnitInterpreter._
import sttp.tapir._

object TapirHelloWorld {
  val helloWorld = endpoint.get
    .in("hello")
    .in(query[String]("name"))
    .out(stringBody)
    .serverLogic[Id](name => Right(s"Hello $name!"))

  def main(args: Array[String]): Unit =
    snunit.SyncServerBuilder
      .build(toHandler(helloWorld :: Nil))
      .listen()
}
