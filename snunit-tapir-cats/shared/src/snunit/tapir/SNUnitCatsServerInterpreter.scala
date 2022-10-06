package snunit.tapir

import cats.effect.Sync
import sttp.tapir.integ.cats.CatsMonadError

class SNUnitCatsServerInterpreter[F[_]: Async] extends SNUnitGenericServerInterpreter {
  private[tapir] type Wrapper[T] = F[T]
  private[tapir] implicit val monadError = new CatsMonadError[F]
}
