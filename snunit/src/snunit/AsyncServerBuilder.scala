package snunit

import snunit.unsafe.{*, given}

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

object AsyncServerBuilder {
  private val initArray: Array[Byte] = new Array[Byte](sizeof[nxt_unit_init_t].toInt)
  private val init: nxt_unit_init_t_* = initArray.at(0).asInstanceOf[nxt_unit_init_t_*]
  def setRequestHandler(requestHandler: RequestHandler): this.type = {
    ServerBuilder.setRequestHandler(requestHandler)
    this
  }
  def setWebsocketHandler(websocketHandler: WebsocketHandler): this.type = {
    ServerBuilder.setWebsocketHandler(websocketHandler)
    this
  }
  private var shutdownHandler: (() => Unit) => Unit = shutdown => shutdown()
  def setShutdownHandler(shutdownHandler: (() => Unit) => Unit): this.type = {
    this.shutdownHandler = shutdownHandler
    this
  }
  def build(): AsyncServer = {
    ServerBuilder.setBaseHandlers(init)
    init.callbacks.add_port = AsyncServerBuilder.add_port
    init.callbacks.remove_port = AsyncServerBuilder.remove_port
    init.callbacks.quit = AsyncServerBuilder.quit
    val ctx: nxt_unit_ctx_t_* = nxt_unit_init(init)
    if (ctx.isNull) {
      throw new Exception("Failed to create Unit object")
    }
    new AsyncServer(ctx)
  }

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

  private val remove_port: remove_port_t = remove_port_t {
    (_: nxt_unit_t_*, ctx: nxt_unit_ctx_t_*, port: nxt_unit_port_t_*) =>
      {
        if (port.data != null && !ctx.isNull) {
          PortData.fromPort(port).stop()
        }
      }
  }

  private val quit: quit_t = quit_t { (ctx: nxt_unit_ctx_t_*) =>
    shutdownHandler { () =>
      nxt_unit_done(ctx)
    }
  }

  private class PortData private (
      val ctx: nxt_unit_ctx_t_*,
      val port: nxt_unit_port_t_*
  ) {
    private var stopped: Boolean = false
    val process_port_msg: Runnable = new Runnable {
      def run(): Unit = {
        val rc = nxt_unit_process_port_msg(ctx, port)

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

        val continue = rc == NXT_UNIT_OK || continueReading

        if (stopped && PortData.isLastFDStopped) {
          shutdownHandler { () =>
            nxt_unit_done(ctx)
          }
        } else if (continue) {
          // The NGINX Unit implementation uses a cancelable timer
          // so it can cancel this callback in stop()
          // We don't do that here, also because doing that would
          // allocate a new Lambda for every process_port_msg
          // and it's hard to cancel since it happens immediately
          EventPollingExecutorScheduler.execute(process_port_msg)
        }
      }
    }

    val stopMonitorCallback: Runnable = EventPollingExecutorScheduler.monitorReads(port.in_fd, process_port_msg)

    def stop(): Unit = {
      stopped = true
      stopMonitorCallback.run()
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
