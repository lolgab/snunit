package snunit.tapir

import sttp.tapir.server._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

object SNUnitFutureServerInterpreter extends SNUnitGenericServerInterpreter {
  private[tapir] type Wrapper[T] = Future[T]
  private[tapir] type HandlerWrapper = snunit.Handler
  private[tapir] val dispatcher = new WrapperDispatcher {
    @inline def dispatch(f: => Future[Unit]): Unit = f
  }
  @inline private[tapir] def createHandleWrapper(f: => snunit.Handler): HandlerWrapper = f
  @inline private[tapir] def wrapSideEffect[T](f: => T): Wrapper[T] = Future.successful(f)
  implicit private[tapir] val monadError: sttp.monad.MonadError[Future] = new sttp.monad.FutureMonad
}
