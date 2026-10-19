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

  override def run = (unitConfig, routes).tupled.use { case (config, r) =>
    SNUnitServerBuilder
      .default[IO]
      .withConfig(config)
      .withHttpApp(r)
      .run
  }
}
