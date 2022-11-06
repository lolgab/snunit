package snunit.tests

import cats.effect._
import snunit.tapir._
import sttp.tapir._

object TapirHelloWorldIO extends epollcat.EpollApp.Simple {
  val helloWorld = endpoint.get
    .in("hello")
    .in(query[String]("name"))
    .out(stringBody)
    .serverLogic[IO](name => IO.delay(Right(s"Hello $name!")))

  def run =
    SNUnitServerBuilder
      .default[IO]
      .withServerEndpoints(helloWorld :: Nil)
      .build
      .useForever
}
