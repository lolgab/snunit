package snunit.http4s

import org.http4s.HttpApp
import cats.effect.IO
import org.http4s.Response
import org.http4s.Status

class SNUnitServerBuilder(
    private val httpApp: HttpApp[IO],
    private val errorHandler: Throwable => IO[Response[IO]]
) {
  private def copy(
      httpApp: HttpApp[IO] = this.httpApp,
      errorHandler: Throwable => IO[Response[IO]] = this.errorHandler
  ) = new SNUnitServerBuilder(
    httpApp = httpApp,
    errorHandler = errorHandler
  )
  def withErrorHandler(errorHandler: Throwable => IO[Response[IO]]): SNUnitServerBuilder =
    copy(errorHandler = errorHandler)
  def withHttpApp(httpApp: HttpApp[IO]): SNUnitServerBuilder = copy(httpApp = httpApp)
  def build: IO[snunit.AsyncServer] = Impl.buildServer(httpApp, errorHandler)
}
object SNUnitServerBuilder {
  def default: SNUnitServerBuilder = {
    val serverFailure = Response(Status.InternalServerError).putHeaders(org.http4s.headers.`Content-Length`.zero)
    def errorHandler: Throwable => IO[Response[IO]] = { case (_: Throwable) =>
      IO.pure(serverFailure.covary[IO])
    }
    new SNUnitServerBuilder(
      httpApp = HttpApp.notFound[IO],
      errorHandler = errorHandler
    )
  }
}
