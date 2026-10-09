package snunit.tests

import cats.effect.*
import snunit.tapir.*
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.*

object TapirWebsocket extends IOApp.Simple {
  val echo = endpoint.get
    .in("echo")
    .out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](Fs2Streams[IO]))
    .serverLogicSuccess[IO](_ => IO.pure(identity))

  def run =
    SNUnitServerBuilder
      .default[IO]
      .withServerEndpoints(echo :: Nil)
      .run
}
