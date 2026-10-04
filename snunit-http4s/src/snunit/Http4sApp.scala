package snunit

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import org.http4s.HttpApp
import snunit.http4s.SNUnitServerBuilder

trait Http4sApp extends IOApp.Simple {
  def routes: Resource[IO, HttpApp[IO]]

  // libunit contexts must not be used from several threads concurrently
  override protected def computeWorkerThreadCount: Int = 1

  override def run = routes.use { r =>
    SNUnitServerBuilder
      .default[IO]
      .withHttpApp(r)
      .run
  }
}
