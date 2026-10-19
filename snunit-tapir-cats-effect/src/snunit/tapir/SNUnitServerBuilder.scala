package snunit.tapir

import cats.effect._
import cats.effect.std.Dispatcher
import cats.implicits._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.model._
import sttp.tapir._
import sttp.tapir.server._
import sttp.tapir.server.interceptor.Interceptor

class SNUnitServerBuilder[F[_]: Async: LiftIO] private (
    serverEndpoints: List[ServerEndpoint[Fs2Streams[F] & WebSockets, F]],
    config: snunit.config.UnitConfig,
    interceptors: List[Interceptor[F]]
) {

  private def copy(
      serverEndpoints: List[ServerEndpoint[Fs2Streams[F] & WebSockets, F]] = this.serverEndpoints,
      config: snunit.config.UnitConfig = this.config,
      interceptors: List[Interceptor[F]] = this.interceptors
  ) = new SNUnitServerBuilder(
    serverEndpoints = serverEndpoints,
    config = config,
    interceptors = interceptors
  )

  /** Configures FreeUnit and the standalone executable. See [[snunit.config.UnitConfig]]. */
  def withConfig(config: snunit.config.UnitConfig): SNUnitServerBuilder[F] = copy(config = config)

  /** Tapir interceptors applied to every request, for example the tracing of `snunit-otel4s`. */
  def withInterceptors(interceptors: List[Interceptor[F]]): SNUnitServerBuilder[F] = copy(interceptors = interceptors)

  def withServerEndpoints(
      serverEndpoints: List[ServerEndpoint[Fs2Streams[F] & WebSockets, F]]
  ): SNUnitServerBuilder[F] = {
    copy(serverEndpoints = serverEndpoints)
  }

  def run: F[Unit] = for
    shutdownDeferred <- Deferred[IO, IO[Unit]].to[F]
    pollers <- IO.pollers.to[F]
    shutdown <- Dispatcher.parallel[F](await = true).use { dispatcher =>
      for
        handler <- new SNUnitCatsServerInterpreter[F](dispatcher, interceptors).toHandler(serverEndpoints)
        _ <- snunit.CEAsyncServerBuilder
          .setConfig(config)
          .setDispatcher(dispatcher)
          .setFileDescriptorPoller(pollers.head.asInstanceOf)
          .setShutdownDeferred(shutdownDeferred)
          .setRequestHandler(handler)
          .setWebsocketHandler(SNUnitWebSockets)
          .build
          .to[F]
        shutdown <- shutdownDeferred.get.to[F]
      yield shutdown
    }
    _ <- shutdown.to[F]
  yield ()
}

object SNUnitServerBuilder {
  def default[F[_]: Async: LiftIO]: SNUnitServerBuilder[F] = {
    new SNUnitServerBuilder[F](
      serverEndpoints = endpoint.out(statusCode(StatusCode.NotFound)).serverLogicSuccess(_ => Async[F].pure(())) :: Nil,
      config = snunit.config.UnitConfig(),
      interceptors = Nil
    )
  }
}
