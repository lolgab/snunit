package snunit

import cats.effect.{IO, Resource}
import org.http4s.HttpApp
import snunit.http4s.SNUnitServerBuilder

trait Http4sApp extends epollcat.EpollApp.Simple {
  def routes: Resource[IO, HttpApp[IO]]

  override def run =
    val resource =
      for
        r <- routes
        server <- SNUnitServerBuilder
          .default[IO]
          .withHttpApp(r)
          .build
      yield server

    resource.useForever
}
