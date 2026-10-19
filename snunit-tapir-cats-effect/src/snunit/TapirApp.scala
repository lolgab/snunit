package snunit

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.ResourceIO
import cats.syntax.all.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.Interceptor

trait TapirApp extends IOApp.Simple {
  def serverEndpoints: Resource[IO, List[ServerEndpoint[Any, IO]]]

  /** Configures FreeUnit and the standalone executable. */
  def unitConfig: ResourceIO[snunit.config.UnitConfig] = Resource.pure(snunit.config.UnitConfig())

  /** Tapir interceptors applied to every request, for example the tracing of [[TapirOtel4s]]. It is acquired before
    * `serverEndpoints`.
    */
  def serverInterceptors: Resource[IO, List[Interceptor[IO]]] = Resource.pure(Nil)

  override def run = (unitConfig, serverInterceptors, serverEndpoints).tupled.use { case (config, interceptors, se) =>
    tapir.SNUnitServerBuilder
      .default[IO]
      .withConfig(config)
      .withInterceptors(interceptors)
      .withServerEndpoints(se)
      .run
  }
}
