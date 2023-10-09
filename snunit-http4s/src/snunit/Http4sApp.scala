package snunit

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import org.http4s.HttpApp
import snunit.http4s.SNUnitServerBuilder

trait Http4sApp extends IOApp.Simple {
  def routes: Resource[IO, HttpApp[IO]]

  override def run = routes.use { r =>
    SNUnitServerBuilder
      .default[IO]
      .withHttpApp(r)
      .run
  }
}
