package snunit.tapir

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SNUnitInterpreterFuture extends SNUnitInterpreterGeneric {
  private[tapir] type F[T] = Future[T]
  implicit private[tapir] val monadError: sttp.monad.MonadError[Future] = new sttp.monad.FutureMonad
}
