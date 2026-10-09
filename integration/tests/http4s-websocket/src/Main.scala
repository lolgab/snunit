package snunit.tests

import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import snunit.http4s.SNUnitServerBuilder

object Http4sWebsocket extends IOApp.Simple {
  def run =
    SNUnitServerBuilder
      .default[IO]
      .withHttpWebSocketApp { webSocketBuilder =>
        HttpRoutes
          .of[IO] { case GET -> Root / "echo" =>
            webSocketBuilder.build(identity)
          }
          .orNotFound
      }
      .run
}
