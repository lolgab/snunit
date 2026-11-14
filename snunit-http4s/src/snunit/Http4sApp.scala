package snunit

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.ResourceIO
import cats.syntax.all.*
import org.http4s.HttpApp
import snunit.http4s.SNUnitServerBuilder

trait Http4sApp extends IOApp.Simple {
  def routes: Resource[IO, HttpApp[IO]]

  /** Configures FreeUnit and the standalone executable. */
  def unitConfig: ResourceIO[snunit.config.UnitConfig] = Resource.pure(snunit.config.UnitConfig())

  /** Maximum number of requests that a process runs at the same time, `None` for no limit. See `withConcurrencyLimit`
    * of `SNUnitServerBuilder`.
    */
  def concurrencyLimit: Option[Int] = None

  override def run = (unitConfig, routes).tupled.use { case (config, r) =>
    SNUnitServerBuilder
      .default[IO]
      .withConfig(config)
      .withConcurrencyLimit(concurrencyLimit)
      .withHttpApp(r)
      .run
  }
}
