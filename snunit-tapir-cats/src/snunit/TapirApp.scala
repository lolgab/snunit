package snunit

import cats.effect.{IO, Resource}
import sttp.tapir.server.ServerEndpoint

trait TapirApp extends epollcat.EpollApp.Simple {
  def serverEndpoints: Resource[IO, List[ServerEndpoint[Any, IO]]]

  override def run = serverEndpoints.use { se =>
    tapir.SNUnitServerBuilder
      .default[IO]
      .withServerEndpoints(se)
      .run
  }
}
