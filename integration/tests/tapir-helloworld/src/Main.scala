package snunit.tests

import snunit.tapir.SNUnitIdServerInterpreter._
import sttp.tapir._

object TapirHelloWorld {
  val helloWorld = endpoint.get
    .in("hello")
    .in(query[String]("name"))
    .out(stringBody)
    .serverLogic[Id](name => Right(s"Hello $name!"))

  def main(args: Array[String]): Unit =
    snunit.SyncServerBuilder
      .setRequestHandler(toHandler(helloWorld :: Nil))
      .build()
      .listen()
}
