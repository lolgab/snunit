package snunit

import snunit.unsafe.{*, given}

import cats.effect.*
import cats.effect.std.Dispatcher

import scala.annotation.tailrec
import scala.scalanative.libc.errno.errno
import scala.scalanative.libc.string.strerror
import scala.scalanative.posix.fcntl._
import scala.scalanative.posix.fcntlOps._
import scala.scalanative.posix.sys.ioctl._
import scala.scalanative.runtime.Intrinsics
import scala.scalanative.runtime.fromRawPtr
import scala.scalanative.runtime.toRawPtr
import scala.scalanative.unsafe.*
import scala.util.control.NonFatal
import scala.concurrent.Future

private[snunit] object CEAsyncServerBuilder {
  private val initArray: Array[Byte] = new Array[Byte](sizeof[nxt_unit_init_t].toInt)
  private val init: nxt_unit_init_t_* = initArray.at(0).asInstanceOf[nxt_unit_init_t_*]

  private var dispatcher: Dispatcher[IO] = dispatcher
  def setDispatcher[F[_]: LiftIO](dispatcher: Dispatcher[F]): this.type =
    this.dispatcher = new Dispatcher[IO] {
      override def unsafeToFutureCancelable[A](fa: IO[A]): (Future[A], () => Future[Unit]) =
        dispatcher.unsafeToFutureCancelable[A](summon[LiftIO[F]].liftIO(fa))
    }
    this

  private var poller: FileDescriptorPoller = null
  def setFileDescriptorPoller(poller: FileDescriptorPoller): this.type =
    this.poller = poller
    this

  private var shutdownDeferred: Deferred[IO, IO[Unit]] = null
  def setShutdownDeferred(deferred: Deferred[IO, IO[Unit]]): this.type =
    this.shutdownDeferred = deferred
    this

  def setRequestHandler(requestHandler: RequestHandler): this.type = {
    ServerBuilder.setRequestHandler(requestHandler)
    this
  }
  def setWebsocketHandler(websocketHandler: WebsocketHandler): this.type = {
    ServerBuilder.setWebsocketHandler(websocketHandler)
    this
  }
  def build: IO[Unit] = IO {
    ServerBuilder.setBaseHandlers(init)
    init.callbacks.add_port = CEAsyncServerBuilder.add_port
    init.callbacks.remove_port = CEAsyncServerBuilder.remove_port
    init.callbacks.quit = CEAsyncServerBuilder.quit
    nxt_unit_init(init)
  }.flatMap(ctx =>
    if (ctx.isNull) IO.raiseError(new Exception("Failed to create Unit object"))
    else IO.unit
  )

  private val add_port: add_port_t = add_port_t { (ctx: nxt_unit_ctx_t_*, port: nxt_unit_port_t_*) =>
    {
      if (port.in_fd != -1) {
        var result = NXT_UNIT_OK
        locally {
          val res = fcntl(port.in_fd, F_SETFL, O_NONBLOCK)
          if (res == -1) {
            nxt_unit_warn(ctx, s"fcntl(${port.in_fd}, O_NONBLOCK) failed: ${fromCString(strerror(errno))}, $errno)")
            result = -1
          }
        }
        if (result == NXT_UNIT_OK) {
          try {
            PortData.register(ctx, port)
            NXT_UNIT_OK
          } catch {
            case NonFatal(e @ _) =>
              nxt_unit_warn(ctx, s"Polling failed: ${fromCString(strerror(errno))}, $errno)")
              NXT_UNIT_ERROR
          }
        } else result
      } else NXT_UNIT_OK
    }
  }

  private val remove_port: remove_port_t =
    remove_port_t { (_: nxt_unit_t_*, ctx: nxt_unit_ctx_t_*, port: nxt_unit_port_t_*) =>
      {
        if (port.data != null && !ctx.isNull) {
          val portData = PortData.fromPort(port)
          portData.stop()
        }
      }
    }

  private val quit: quit_t = quit_t { (ctx: nxt_unit_ctx_t_*) =>
    dispatcher.unsafeRunAndForget(shutdownDeferred.complete(IO(nxt_unit_done(ctx))))
  }

  private class PortData private (
      val ctx: nxt_unit_ctx_t_*,
      val port: nxt_unit_port_t_*
  ) {
    private var stopped: Boolean = false

    // ideally this shouldn't be needed.
    // in theory rc == NXT_UNIT_AGAIN
    // would mean that there aren't any messages
    // to read. In practice if we stop at rc == NXT_UNIT_AGAIN
    // there are some unprocessed messages which effect in
    // epollcat (which uses edge-triggering) to hang on close
    // since one port to remain open and one callback registered
    def continueReading: Boolean = {
      val bytesAvailable = stackalloc[Int]()
      ioctl(port.in_fd, FIONREAD, bytesAvailable.asInstanceOf[Ptr[Byte]])
      !bytesAvailable > 0
    }

    dispatcher
      .unsafeRunAndForget(
        poller
          .registerFileDescriptor(port.in_fd, monitorReadReady = true, monitorWriteReady = false)
          .use(handle =>
            handle
              .pollReadRec[Unit, Unit](()) { _ =>
                IO {
                  // process messages until we are blocked
                  while (nxt_unit_process_port_msg(ctx, port) == NXT_UNIT_OK || continueReading) {}

                  if (stopped && PortData.isLastFDStopped)
                    Right(())
                  else
                    // suspend until more data is available on the socket, then we will be invoked again
                    Left(())
                }
              }
              .race(shutdownDeferred.get)
          )
      )

    def stop(): Unit = {
      stopped = true
      PortData.stopped.put(this, ())
    }
  }

  private object PortData {
    private[this] val references = new java.util.IdentityHashMap[PortData, Unit]

    private[this] val stopped = new java.util.IdentityHashMap[PortData, Unit]

    def isLastFDStopped: Boolean = references == stopped

    def register(ctx: nxt_unit_ctx_t_*, port: nxt_unit_port_t_*): Unit =
      val portData = new PortData(ctx, port)
      references.put(portData, ())
      port.data = fromRawPtr(Intrinsics.castObjectToRawPtr(portData))

    def fromPort(port: nxt_unit_port_t_*): PortData = {
      Intrinsics.castRawPtrToObject(toRawPtr(port.data)).asInstanceOf[PortData]
    }
  }
}
