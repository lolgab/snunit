package snunit.tapir

import scala.concurrent._

class SNUnitFutureServerInterpreter()(implicit ec: ExecutionContext) extends SNUnitGenericServerInterpreter {
  private[tapir] type Wrapper[T] = Future[T]
  private[tapir] type HandlerWrapper[T] = T
  private[tapir] val dispatcher = new WrapperDispatcher {
    @inline def dispatch[T](f: => Future[T]): Unit = f
  }
  @inline private[tapir] def createHandleWrapper[T](f: => T): HandlerWrapper[T] = f
  @inline private[tapir] def wrapSideEffect[T](f: => T): Wrapper[T] = Future.successful(f)
  implicit private[tapir] val monadError: sttp.monad.MonadError[Future] = new sttp.monad.FutureMonad
}

object SNUnitFutureServerInterpreter extends SNUnitFutureServerInterpreter()(ExecutionContext.global) {
  def apply()(implicit ex: ExecutionContext): SNUnitFutureServerInterpreter = {
    new SNUnitFutureServerInterpreter()
  }
}
