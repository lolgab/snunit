package snunit.tapir

import sttp.tapir.server._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

object SNUnitFutureServerInterpreter extends SNUnitGenericServerInterpreter {
  private[tapir] type Wrapper[T] = Future[T]
  private[tapir] type HandlerWrapper[T] = T
  private[tapir] val dispatcher = new WrapperDispatcher {
    @inline def dispatch[T](f: => Future[T]): Unit = f
  }
  @inline private[tapir] def createHandleWrapper[T](f: => T): HandlerWrapper[T] = f
  // TODO: Remove once in bin-compat breaking window
  @inline override def toHandler(endpoints: List[ServerEndpoint[Any, Future]]): snunit.Handler =
    super.toHandler(endpoints)
  @inline private[tapir] def wrapSideEffect[T](f: => T): Wrapper[T] = Future.successful(f)
  implicit private[tapir] val monadError: sttp.monad.MonadError[Future] = new sttp.monad.FutureMonad
}
