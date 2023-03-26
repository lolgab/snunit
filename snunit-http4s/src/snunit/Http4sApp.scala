package snunit

import cats.effect.IO
import org.http4s.HttpApp
import snunit.http4s.SNUnitServerBuilder

trait Http4sApp extends epollcat.EpollApp.Simple {
  def routes: HttpApp[IO]

  override def run = SNUnitServerBuilder
    .default[IO]
    .withHttpApp(routes)
    .build
    .useForever
}
