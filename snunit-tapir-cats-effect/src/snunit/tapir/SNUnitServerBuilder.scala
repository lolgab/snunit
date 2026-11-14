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
    interceptors: List[Interceptor[F]],
    concurrencyLimit: Option[Int]
) {

  private def copy(
      serverEndpoints: List[ServerEndpoint[Fs2Streams[F] & WebSockets, F]] = this.serverEndpoints,
      config: snunit.config.UnitConfig = this.config,
      interceptors: List[Interceptor[F]] = this.interceptors,
      concurrencyLimit: Option[Int] = this.concurrencyLimit
  ) = new SNUnitServerBuilder(
    serverEndpoints = serverEndpoints,
    config = config,
    interceptors = interceptors,
    concurrencyLimit = concurrencyLimit
  )

  /** Configures FreeUnit and the standalone executable. See [[snunit.config.UnitConfig]]. */
  def withConfig(config: snunit.config.UnitConfig): SNUnitServerBuilder[F] = copy(config = config)

  /** Maximum number of requests that this process runs at the same time, which must be positive. At the limit the
    * process stops taking requests from FreeUnit: they wait in the queue of the application, where another process can
    * take them, until a request is done. A request that waits longer than the `timeout` of [[snunit.config.Limits]]
    * gets 503. A websocket counts until it is closed. The first requests of a process can go over the limit, by at most
    * one per thread of the router.
    */
  def withConcurrencyLimit(limit: Int): SNUnitServerBuilder[F] = {
    require(limit > 0, s"the concurrency limit must be positive, got $limit")
    copy(concurrencyLimit = Some(limit))
  }

  /** Doesn't limit the requests that this process runs at the same time. This is the default. */
  def withoutConcurrencyLimit: SNUnitServerBuilder[F] = copy(concurrencyLimit = None)

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
          .setConcurrencyLimit(concurrencyLimit)
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
      interceptors = Nil,
      concurrencyLimit = None
    )
  }
}
