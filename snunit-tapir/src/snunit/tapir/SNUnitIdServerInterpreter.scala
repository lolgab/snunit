package snunit.tapir

import sttp.monad._

object SNUnitIdServerInterpreter extends SNUnitGenericServerInterpreter {
  type Id[T] = T
  private[tapir] type Wrapper[T] = Id[T]
  private[tapir] implicit val monadError: MonadError[Wrapper] = new MonadError[Id] {
    override def unit[T](t: T): Id[T] = t
    override def map[T, T2](fa: Id[T])(f: T => T2): Id[T2] = f(fa)
    override def flatMap[T, T2](fa: Id[T])(f: T => Id[T2]): Id[T2] = f(fa)
    override def error[T](t: Throwable): Id[T] = throw t
    override protected def handleWrappedError[T](rt: Id[T])(h: PartialFunction[Throwable, Id[T]]): Id[T] = rt
    override def ensure[T](f: Id[T], e: => Id[Unit]): Id[T] = try f
    finally e
  }
}
