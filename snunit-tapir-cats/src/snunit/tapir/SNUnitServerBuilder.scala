package snunit.tapir

import cats.effect._
import cats.effect.std.Dispatcher
import cats.implicits._
import sttp.model._
import sttp.tapir._
import sttp.tapir.server._

class SNUnitServerBuilder[F[_]: Async] private (serverEndpoints: List[ServerEndpoint[Any, F]]) {

  private def copy(serverEndpoints: List[ServerEndpoint[Any, F]]) = new SNUnitServerBuilder(
    serverEndpoints = serverEndpoints
  )

  def withServerEndpoints(serverEndpoints: List[ServerEndpoint[Any, F]]): SNUnitServerBuilder[F] = {
    copy(serverEndpoints = serverEndpoints)
  }

  def build: Resource[F, snunit.AsyncServer] = Dispatcher[F].flatMap { dispatcher =>
    val interpreter = new SNUnitCatsServerInterpreter[F](dispatcher)
    Resource.eval {
      for {
        handler <- interpreter.toHandler(serverEndpoints)
        server <- Async[F].delay(snunit.AsyncServerBuilder.build(handler))
      } yield server
    }
  }
}
object SNUnitServerBuilder {
  def default[F[_]: Async]: SNUnitServerBuilder[F] = {
    new SNUnitServerBuilder[F](
      serverEndpoints = endpoint.out(statusCode(StatusCode.NotFound)).serverLogicSuccess(_ => Async[F].pure(())) :: Nil
    )
  }
}
