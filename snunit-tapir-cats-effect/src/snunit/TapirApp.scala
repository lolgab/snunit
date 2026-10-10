package snunit

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.ResourceIO
import cats.syntax.all.*
import sttp.tapir.server.ServerEndpoint

trait TapirApp extends IOApp.Simple {
  def serverEndpoints: Resource[IO, List[ServerEndpoint[Any, IO]]]

  /** Configures FreeUnit and the standalone executable. */
  def unitConfig: ResourceIO[snunit.config.UnitConfig] = Resource.pure(snunit.config.UnitConfig())

  override def run = (unitConfig, serverEndpoints).tupled.use { case (config, se) =>
    tapir.SNUnitServerBuilder
      .default[IO]
      .withConfig(config)
      .withServerEndpoints(se)
      .run
  }
}
