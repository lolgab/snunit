package snunit.tests

import cats.effect.*
import snunit.tapir.*
import sttp.tapir.*

object TapirHelloWorldIO extends IOApp.Simple {
  val helloWorld = endpoint.get
    .in("hello")
    .in(query[String]("name"))
    .out(stringBody)
    .serverLogic[IO](name => IO.delay(Right(s"Hello $name!")))

  def run =
    SNUnitServerBuilder
      .default[IO]
      .withServerEndpoints(helloWorld :: Nil)
      .run
}
