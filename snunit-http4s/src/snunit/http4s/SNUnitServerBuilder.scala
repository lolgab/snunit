package snunit.http4s

import cats.effect.Resource
import cats.effect.LiftIO
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import org.http4s.HttpApp
import org.http4s.Response
import org.http4s.Status

class SNUnitServerBuilder[F[_]: Async: LiftIO](
    private val httpApp: SNUnitWebSocketBuilder[F] => HttpApp[F],
    private val errorHandler: Throwable => F[Response[F]],
    private val config: snunit.config.UnitConfig,
    private val concurrencyLimit: Int
) {
  private def copy(
      httpApp: SNUnitWebSocketBuilder[F] => HttpApp[F] = this.httpApp,
      errorHandler: Throwable => F[Response[F]] = this.errorHandler,
      config: snunit.config.UnitConfig = this.config,
      concurrencyLimit: Int = this.concurrencyLimit
  ) = new SNUnitServerBuilder[F](
    httpApp = httpApp,
    errorHandler = errorHandler,
    config = config,
    concurrencyLimit = concurrencyLimit
  )

  /** Configures FreeUnit and the standalone executable. See [[snunit.config.UnitConfig]]. */
  def withConfig(config: snunit.config.UnitConfig): SNUnitServerBuilder[F] = copy(config = config)

  /** Maximum number of requests that this process runs at the same time, 0 (the default) for no limit. At the limit the
    * process stops taking requests from FreeUnit: they wait in the queue of the application, where another process can
    * take them, until a request is done. A request that waits longer than the `timeout` of [[snunit.config.Limits]]
    * gets 503. A websocket counts until it is closed. The first requests of a process can go over the limit, by at most
    * one per thread of the router.
    */
  def withConcurrencyLimit(limit: Int): SNUnitServerBuilder[F] = copy(concurrencyLimit = limit)
  def withErrorHandler(errorHandler: Throwable => F[Response[F]]): SNUnitServerBuilder[F] =
    copy(errorHandler = errorHandler)
  def withHttpApp(httpApp: HttpApp[F]): SNUnitServerBuilder[F] = copy(httpApp = _ => httpApp)

  /** Like `withHttpApp`, but gives access to the websocket builder (`SNUnitWebSocketBuilder`) to create websocket
    * routes
    */
  def withHttpWebSocketApp(httpApp: SNUnitWebSocketBuilder[F] => HttpApp[F]): SNUnitServerBuilder[F] =
    copy(httpApp = httpApp)
  def run: F[Unit] = Impl.buildServer[F](httpApp, errorHandler, config, concurrencyLimit)

}
object SNUnitServerBuilder {
  def default[F[_]: Async: LiftIO]: SNUnitServerBuilder[F] = {
    val serverFailure = Response(Status.InternalServerError).putHeaders(org.http4s.headers.`Content-Length`.zero)
    def errorHandler: Throwable => F[Response[F]] = { case (_: Throwable) =>
      Async[F].pure(serverFailure.covary[F])
    }
    new SNUnitServerBuilder[F](
      httpApp = _ => HttpApp.notFound[F],
      errorHandler = errorHandler,
      config = snunit.config.UnitConfig(),
      concurrencyLimit = 0
    )
  }
}
