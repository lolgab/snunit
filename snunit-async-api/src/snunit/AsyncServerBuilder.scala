package snunit

import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._

import scala.scalanative.libc.errno.errno
import scala.scalanative.libc.string.strerror
import scala.scalanative.posix.fcntl.F_SETFL
import scala.scalanative.posix.fcntl.O_NONBLOCK
import scala.scalanative.posix.fcntl.fcntl
import scala.scalanative.runtime.ByteArray
import scala.scalanative.unsafe._
import scala.util.control.NonFatal

object AsyncServerBuilder {
  private val initArray: Array[Byte] = new Array[Byte](sizeof[nxt_unit_init_t].toInt)
  private val init: Ptr[nxt_unit_init_t] = {
    initArray.asInstanceOf[ByteArray].at(0).asInstanceOf[Ptr[nxt_unit_init_t]]
  }
  def build(handler: Handler): AsyncServer = {
    ServerBuilder.setBaseHandlers(init, handler)
    init.callbacks.add_port = AsyncServerBuilder.add_port
    init.callbacks.remove_port = AsyncServerBuilder.remove_port
    val ctx: Ptr[nxt_unit_ctx_t] = nxt_unit_init(init)
    if (ctx == null) {
      throw new Exception("Failed to create Unit object")
    }
    new AsyncServer(ctx)
  }

  private val add_port: add_port_t = (ctx: Ptr[nxt_unit_ctx_t], port: Ptr[nxt_unit_port_t]) => {
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
          val runnable = EventPollingExecutorScheduler.monitor(
            port.in_fd,
            reads = true,
            writes = false,
            (_, _) => nxt_unit_process_port_msg(ctx, port)
          )
          ctx.data = RunnableUtils.toPtr(runnable)
          NXT_UNIT_OK
        } catch {
          case NonFatal(e @ _) =>
            nxt_unit_warn(ctx, s"Polling failed: ${fromCString(strerror(errno))}, $errno)")
            NXT_UNIT_ERROR
        }
      } else result
    } else NXT_UNIT_OK
  }

  private val remove_port: remove_port_t = (_: Ptr[nxt_unit_t], port: Ptr[nxt_unit_port_t]) => {
    RunnableUtils.fromPtr(port.data).run()
  }
}
