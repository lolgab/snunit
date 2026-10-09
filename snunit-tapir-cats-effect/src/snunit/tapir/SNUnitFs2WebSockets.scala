package snunit.tapir

import cats.effect.*
import cats.effect.implicits.*
import cats.effect.std.{Dispatcher, Queue}
import cats.syntax.all.*
import fs2.Stream
import snunit.*
import sttp.tapir.WebSocketBodyOutput
import sttp.ws.WebSocketFrame

private[tapir] object SNUnitFs2WebSockets {

  /** Registers the connection of `req` and returns the action which runs `pipe` over its frames, to be executed once
    * `req` is upgraded.
    */
  def prepare[F[_]: Async, REQ, RESP](
      dispatcher: Dispatcher[F],
      req: Request,
      pipe: fs2.Pipe[F, REQ, RESP],
      o: WebSocketBodyOutput[?, REQ, RESP, ?, ?]
  ): F[F[Unit]] = {
    val F = Async[F]
    val sender = new SNUnitWebSockets.Sender(req)
    def sendFrame(frame: WebSocketFrame): F[Unit] = F.delay(sender.send(frame))
    def decode(frame: WebSocketFrame): F[REQ] = F.delay(SNUnitWebSockets.decode(o, frame))

    Queue.unbounded[F, Option[WebSocketFrame]].flatMap { queue =>
      val incoming: Stream[F, REQ] = Stream
        .fromQueueNoneTerminated(queue)
        .evalMap[F, Option[REQ]] {
          case WebSocketFrame.Ping(payload) =>
            if (o.autoPongOnPing) sendFrame(WebSocketFrame.Pong(payload)).as(None)
            else decode(WebSocketFrame.Ping(payload)).map(Some(_))
          case _: WebSocketFrame.Pong if o.ignorePong => F.pure(None)
          case close: WebSocketFrame.Close =>
            if (o.decodeCloseRequests) decode(close).map(Some(_)) else F.pure(None)
          case frame => decode(frame).map(Some(_))
        }
        .unNone

      val responses: Stream[F, WebSocketFrame] =
        pipe(incoming).map(o.responses.encode).takeThrough {
          case _: WebSocketFrame.Close => false
          case _                       => true
        }

      val withPings = o.autoPing match {
        case Some((interval, ping)) =>
          System.err.println(s"DBGPING interval=$interval");
          responses.mergeHaltL(
            Stream.awakeEvery[F](interval).evalTap(t => F.delay(System.err.println(s"DBGPING fire $t"))).as(ping)
          )
        case None => responses
      }

      // frames must be enqueued in order, which the parallel dispatcher doesn't guarantee
      val register = Dispatcher.sequential[F].allocated.flatMap { case (sequential, release) =>
        F.delay {
          SNUnitWebSockets.register(
            req,
            o.concatenateFragmentedFrames,
            frame => sequential.unsafeRunAndForget(queue.offer(frame))
          )
        }.as(release)
      }

      val run = withPings
        .evalMap(sendFrame)
        .compile
        .drain
        .handleErrorWith { e =>
          F.delay {
            System.err.println("Error while processing the websocket")
            e.printStackTrace()
          } *> sendFrame(WebSocketFrame.Close(1011, "Internal error"))
        }
        .guarantee(sendFrame(WebSocketFrame.Close(1000, "")).attempt.void)
        .guarantee(F.delay(SNUnitWebSockets.unregister(req)))

      // The pipe is run in the background: the request handler is done once the connection is upgraded
      register.map(release => F.delay(dispatcher.unsafeRunAndForget(run.guarantee(release))))
    }
  }
}
