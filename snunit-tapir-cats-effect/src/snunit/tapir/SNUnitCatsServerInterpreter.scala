package snunit.tapir

import cats.effect._
import cats.effect.std.Dispatcher
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.WebSocketBodyOutput
import sttp.tapir.integ.cats.effect.CatsMonadError

private[tapir] class SNUnitCatsServerInterpreter[F[_]: Async](ceDispatcher: Dispatcher[F])
    extends SNUnitGenericServerInterpreter {
  private[tapir] type Wrapper[T] = F[T]
  private[tapir] type HandlerWrapper = F[snunit.RequestHandler]
  private[tapir] type Caps = Fs2Streams[F] & WebSockets
  private[tapir] type S = Fs2Streams[F]
  private[tapir] val streamsInstance: S = Fs2Streams[F]
  override private[tapir] def webSocketBody[REQ, RESP](
      pipe: Any,
      o: WebSocketBodyOutput[?, REQ, RESP, ?, S]
  ): WebSocketBody = new WebSocketBody(req =>
    SNUnitFs2WebSockets.prepare[F, REQ, RESP](ceDispatcher, req, pipe.asInstanceOf[fs2.Pipe[F, REQ, RESP]], o)
  )
  private[tapir] implicit val monadError = new CatsMonadError[F]
  private[tapir] val dispatcher = new WrapperDispatcher {
    inline def dispatch(f: => F[Unit]): Unit = {
      ceDispatcher.unsafeRunAndForget(f)
    }
  }
  inline private[tapir] def createHandleWrapper(f: => snunit.RequestHandler): HandlerWrapper = Async[F].delay(f)
  inline private[tapir] def wrapSideEffect[T](f: => T): Wrapper[T] = Async[F].delay(f)
}
