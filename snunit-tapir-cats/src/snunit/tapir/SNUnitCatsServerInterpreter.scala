package snunit.tapir

import cats.effect._
import cats.effect.std.Dispatcher
import sttp.tapir.integ.cats.CatsMonadError

private[tapir] class SNUnitCatsServerInterpreter[F[_]: Async](ceDispatcher: Dispatcher[F])
    extends SNUnitGenericServerInterpreter {
  private[tapir] type Wrapper[T] = F[T]
  private[tapir] type HandlerWrapper = F[snunit.Handler]
  private[tapir] implicit val monadError = new CatsMonadError[F]
  private[tapir] val dispatcher = new WrapperDispatcher {
    @inline def dispatch(f: => F[Unit]): Unit = {
      ceDispatcher.unsafeRunAndForget(f)
    }
  }
  @inline private[tapir] def createHandleWrapper(f: => snunit.Handler): HandlerWrapper = Async[F].delay(f)
  @inline private[tapir] def wrapSideEffect[T](f: => T): Wrapper[T] = Async[F].delay(f)
}
