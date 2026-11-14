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

  /** Maximum number of requests that a process runs at the same time, `None` for no limit. See `withConcurrencyLimit`
    * of `SNUnitServerBuilder`.
    */
  def concurrencyLimit: Option[Int] = None

  override def run = (unitConfig, serverEndpoints).tupled.use { case (config, se) =>
    tapir.SNUnitServerBuilder
      .default[IO]
      .withConfig(config)
      .withConcurrencyLimit(concurrencyLimit)
      .withServerEndpoints(se)
      .run
  }
}
