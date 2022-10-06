package snunit.tapir

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SNUnitFutureServerInterpreter extends SNUnitGenericServerInterpreter {
  private[tapir] type Wrapper[T] = Future[T]
  implicit private[tapir] val monadError: sttp.monad.MonadError[Future] = new sttp.monad.FutureMonad
}
