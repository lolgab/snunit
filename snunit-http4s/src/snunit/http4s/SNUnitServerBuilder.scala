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
    private val errorHandler: Throwable => F[Response[F]]
) {
  private def copy(
      httpApp: SNUnitWebSocketBuilder[F] => HttpApp[F] = this.httpApp,
      errorHandler: Throwable => F[Response[F]] = this.errorHandler
  ) = new SNUnitServerBuilder[F](
    httpApp = httpApp,
    errorHandler = errorHandler
  )
  def withErrorHandler(errorHandler: Throwable => F[Response[F]]): SNUnitServerBuilder[F] =
    copy(errorHandler = errorHandler)
  def withHttpApp(httpApp: HttpApp[F]): SNUnitServerBuilder[F] = copy(httpApp = _ => httpApp)

  /** Like `withHttpApp`, but gives access to the websocket builder (`SNUnitWebSocketBuilder`) to create websocket
    * routes
    */
  def withHttpWebSocketApp(httpApp: SNUnitWebSocketBuilder[F] => HttpApp[F]): SNUnitServerBuilder[F] =
    copy(httpApp = httpApp)
  def run: F[Unit] = Impl.buildServer[F](httpApp, errorHandler)

}
object SNUnitServerBuilder {
  def default[F[_]: Async: LiftIO]: SNUnitServerBuilder[F] = {
    val serverFailure = Response(Status.InternalServerError).putHeaders(org.http4s.headers.`Content-Length`.zero)
    def errorHandler: Throwable => F[Response[F]] = { case (_: Throwable) =>
      Async[F].pure(serverFailure.covary[F])
    }
    new SNUnitServerBuilder[F](
      httpApp = _ => HttpApp.notFound[F],
      errorHandler = errorHandler
    )
  }
}
