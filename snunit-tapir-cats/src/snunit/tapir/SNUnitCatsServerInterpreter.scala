package snunit.tapir

import cats.effect._
import sttp.tapir.integ.cats.CatsMonadError
import cats.effect.std.Dispatcher

private[tapir] class SNUnitCatsServerInterpreter[F[_]: Async](ceDispatcher: Dispatcher[F])
    extends SNUnitGenericServerInterpreter {
  private[tapir] type Wrapper[T] = F[T]
  private[tapir] type HandlerWrapper[T] = F[T]
  private[tapir] implicit val monadError = new CatsMonadError[F]
  private[tapir] val dispatcher = new WrapperDispatcher {
    @inline def dispatch[T](f: => F[T]): Unit = {
      ceDispatcher.unsafeRunAndForget(f)
    }
  }
  @inline private[tapir] def createHandleWrapper[T](f: => T): F[T] = Async[F].delay(f)
  @inline private[tapir] def wrapSideEffect[T](f: => T): F[T] = Async[F].delay(f)
}
