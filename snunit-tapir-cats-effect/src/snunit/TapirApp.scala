package snunit

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import sttp.tapir.server.ServerEndpoint

trait TapirApp extends IOApp.Simple {
  def serverEndpoints: Resource[IO, List[ServerEndpoint[Any, IO]]]

  /** Configures FreeUnit and the standalone executable. */
  def unitConfig: snunit.config.UnitConfig = snunit.config.UnitConfig()

  override def run = serverEndpoints.use { se =>
    tapir.SNUnitServerBuilder
      .default[IO]
      .withConfig(unitConfig)
      .withServerEndpoints(se)
      .run
  }
}
