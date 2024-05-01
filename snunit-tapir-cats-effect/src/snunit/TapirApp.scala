package snunit

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import sttp.tapir.server.ServerEndpoint

trait TapirApp extends IOApp.Simple {
  def serverEndpoints: Resource[IO, List[ServerEndpoint[Any, IO]]]

  override def run = serverEndpoints.use { se =>
    tapir.SNUnitServerBuilder
      .default[IO]
      .withServerEndpoints(se)
      .run
  }
}
