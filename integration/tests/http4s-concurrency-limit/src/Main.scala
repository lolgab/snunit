package snunit.tests

import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import snunit.http4s.*

import scala.concurrent.duration.*

/** `/slow` takes half a second. `/max` is the highest number of `/slow` requests that ran at the same time since
  * `/reset`.
  */
object Http4sConcurrencyLimit extends IOApp.Simple {
  def run: IO[Unit] = for {
    running <- Ref[IO].of(0)
    max <- Ref[IO].of(0)
    routes = HttpRoutes.of[IO] {
      case GET -> Root / "slow" =>
        running.updateAndGet(_ + 1).flatMap(n => max.update(_.max(n))) *>
          IO.sleep(500.millis) *>
          running.update(_ - 1) *>
          Ok("done")
      case GET -> Root / "max"   => max.get.flatMap(n => Ok(n.toString))
      case GET -> Root / "reset" => max.set(0) *> Ok("")
    }
    _ <- SNUnitServerBuilder
      .default[IO]
      .withConcurrencyLimit(Some(2))
      .withHttpApp(routes.orNotFound)
      .run
  } yield ()
}
