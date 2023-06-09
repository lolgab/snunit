package snunit

import snunit.unsafe.{*, given}

import scala.scalanative.libc.errno.errno
import scala.scalanative.libc.string.strerror
import scala.scalanative.posix.fcntl._
import scala.scalanative.posix.fcntlOps._
import scala.scalanative.runtime.Intrinsics
import scala.scalanative.runtime.fromRawPtr
import scala.scalanative.runtime.toRawPtr
import scala.scalanative.unsafe.*
import scala.util.control.NonFatal
import scala.annotation.tailrec
import scala.scalanative.posix.sys.ioctl

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
      // println(s"adding port with fd: ${in_fd(port)}")
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
        // println(s"removing port with fd: ${in_fd(port)}")
        // println(s"port.data = ${port.data}")
        // println(s"ctx.isNull = ${ctx.isNull}")
        if (port.data != null && !ctx.isNull) {
          PortData.fromPort(port).stop()
        }
      }
  }

  private class PortData private (
      val ctx: nxt_unit_ctx_t_*,
      val port: nxt_unit_port_t_*
  ) {
    private var stopped: Boolean = false
    private var scheduled: Boolean = false

    private def process_port_msg_impl(): Boolean = {
      val rc = nxt_unit_process_port_msg(ctx, port)
      // println(s"nxt_unit_process_port_msg returned $rc")

      def continueReading: Boolean = {
        // println("checking if we should continue reading...")
        val bytesAvailable = stackalloc[Int]()
        ioctl.ioctl(port.in_fd, ioctl.FIONREAD, bytesAvailable.asInstanceOf[Ptr[Byte]])
        // println(s"should we continue reading: ${!bytesAvailable > 0}")
        !bytesAvailable > 0
      }

      rc == NXT_UNIT_OK || continueReading
    }
    val process_port_msg: Runnable = new Runnable {
      def run(): Unit = {
        val continue = process_port_msg_impl()
        if (continue && !scheduled && !stopped) {
          // The NGINX Unit implementation uses a cancelable timer
          // so it can cancel this callback in stop()
          // We don't do that here, also because doing that would
          // allocate a new Lambda for every process_port_msg
          // and it's hard to cancel since it happens immediately
          EventPollingExecutorScheduler.execute(timer_callback)
          scheduled = true
        }
      }
    }

    // println(s"Monitoring reads on fd: ${port.in_fd}")
    val stopMonitorCallback: Runnable = EventPollingExecutorScheduler.monitorReads(port.in_fd, process_port_msg)
    // println(s"Created stopMonitorCallback ${stopMonitorCallback.hashCode()} for fd: ${port.in_fd}")

    val timer_callback: Runnable = new Runnable {
      def run(): Unit = {
        scheduled = false
        if (!stopped) {
          process_port_msg.run()
        }
      }
    }

    def stop(): Unit = {
      // println(s"calling stop on fd: ${port.in_fd}")
      stopped = true
      // println(s"Calling stopMonitorCallback ${stopMonitorCallback.hashCode()} for fd: ${port.in_fd}")
      stopMonitorCallback.run()
    }
  }

  private object PortData {
    private[this] val references = new java.util.IdentityHashMap[PortData, Unit]

    def register(ctx: nxt_unit_ctx_t_*, port: nxt_unit_port_t_*): Unit =
      val portData = new PortData(ctx, port)
      references.put(portData, ())
      port.data = fromRawPtr(Intrinsics.castObjectToRawPtr(portData))

    def fromPort(port: nxt_unit_port_t_*): PortData = {
      val result = Intrinsics.castRawPtrToObject(toRawPtr(port.data)).asInstanceOf[PortData]
      references.remove(result)
      result
    }
  }
}
