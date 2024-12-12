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
    val parallelHandler = new snunit.RequestHandler {
      def handleRequest(req: snunit.Request) = {
        toHandler(helloWorld :: Nil)
      }
    }
    snunit.SyncServerBuilder
      .setRequestHandler(parallelHandler)
      .build()
      .listen()
}
