package snunit.http4s

import cats.effect.Resource
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import org.http4s.HttpApp
import org.http4s.Response
import org.http4s.Status

class SNUnitServerBuilder[F[_]: Async](
    private val httpApp: HttpApp[F],
    private val errorHandler: Throwable => F[Response[F]]
) {
  private def copy(
      httpApp: HttpApp[F] = this.httpApp,
      errorHandler: Throwable => F[Response[F]] = this.errorHandler
  ) = new SNUnitServerBuilder[F](
    httpApp = httpApp,
    errorHandler = errorHandler
  )
  def withErrorHandler(errorHandler: Throwable => F[Response[F]]): SNUnitServerBuilder[F] =
    copy(errorHandler = errorHandler)
  def withHttpApp(httpApp: HttpApp[F]): SNUnitServerBuilder[F] = copy(httpApp = httpApp)
  def run: F[Unit] = Dispatcher
    .parallel[F](await = true)
    .use { dispatcher =>
      Impl.buildServer[F](dispatcher, httpApp, errorHandler)
    }

}
object SNUnitServerBuilder {
  def default[F[_]: Async]: SNUnitServerBuilder[F] = {
    val serverFailure = Response(Status.InternalServerError).putHeaders(org.http4s.headers.`Content-Length`.zero)
    def errorHandler: Throwable => F[Response[F]] = { case (_: Throwable) =>
      Async[F].pure(serverFailure.covary[F])
    }
    new SNUnitServerBuilder[F](
      httpApp = HttpApp.notFound[F],
      errorHandler = errorHandler
    )
  }
}
