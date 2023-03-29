package snunit

import snunit.unsafe.{*, given}

import scala.scalanative.libc.errno.errno
import scala.scalanative.libc.string.strerror
import scala.scalanative.posix.fcntl.F_SETFL
import scala.scalanative.posix.fcntl.O_NONBLOCK
import scala.scalanative.posix.fcntl.fcntl
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
  def build(): AsyncServer = {
    ServerBuilder.setBaseHandlers(init)
    init.callbacks.add_port = AsyncServerBuilder.add_port
    init.callbacks.remove_port = AsyncServerBuilder.remove_port
    val ctx: nxt_unit_ctx_t_* = nxt_unit_init(init)
    if (ctx.isNull) {
      throw new Exception("Failed to create Unit object")
    }
    new AsyncServer(ctx)
  }

  private val add_port: add_port_t = add_port_t { (ctx: nxt_unit_ctx_t_*, port: nxt_unit_port_t_*) =>
    {
      if (in_fd(port) != -1) {
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
            val portData = new PortData(ctx, port)
            portData.stopMonitorCallback =
              EventPollingExecutorScheduler.monitorReads(port.in_fd, portData.process_port_msg)
            port.data = portData.toPtr()
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
          PortData.fromPtr(port.data).stop()
        }
      }
  }
  private class PortData(
      val ctx: nxt_unit_ctx_t_*,
      val port: nxt_unit_port_t_*
  ) {
    var stopped: Boolean = false
    var scheduled: Boolean = false
    var stopMonitorCallback: Runnable = null

    def toPtr(): Ptr[Byte] = {
      PortData.references.put(this, ())
      fromRawPtr(Intrinsics.castObjectToRawPtr(this))
    }

    val timer_callback: Runnable = new Runnable {
      def run(): Unit = {
        scheduled = false
        if (!stopped) {
          process_port_msg.run()
        }
      }
    }

    val process_port_msg: Runnable = new Runnable {
      def run(): Unit = {
        val rc = nxt_unit_process_port_msg(ctx, port)
        if (rc == NXT_UNIT_OK && !scheduled && !stopped) {
          scheduled = true
          // The NGINX Unit implementation uses a cancelable timer
          // so it can cancel this callback in stop()
          // We don't do that here, also because doing that would
          // allocate a new Lambda for every process_port_msg
          // and it's hard to cancel since it happens immediately
          EventPollingExecutorScheduler.execute(timer_callback)
        }
      }
    }

    def stop(): Unit = {
      // The NGINX Unit implementation sets `stopped = true` here
      // https://github.com/nginx/unit/blob/bba97134e983541e94cf73e93900729e3a3e61fc/src/nodejs/unit-http/unit.cpp#L90
      // But doing so breaks http4s server which doesn't restart properly
      // stopped = true
      stopMonitorCallback.run()
    }
  }

  private object PortData {
    private val references = new java.util.IdentityHashMap[PortData, Unit]

    def fromPtr(ptr: Ptr[Byte]): PortData = {
      val result = Intrinsics.castRawPtrToObject(toRawPtr(ptr)).asInstanceOf[PortData]
      references.remove(result)
      result
    }
  }
}
