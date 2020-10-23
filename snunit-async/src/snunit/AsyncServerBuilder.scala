package snunit

import scala.scalanative.libc.errno.errno
import scala.scalanative.libc.stdlib.malloc
import scala.scalanative.libc.string.strerror
import scala.scalanative.loop.Poll
import scala.scalanative.posix.fcntl.F_SETFL
import scala.scalanative.posix.fcntl.O_NONBLOCK
import scala.scalanative.posix.fcntl.fcntl
import scala.scalanative.unsafe._
import scala.util.control.NonFatal

import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._

object AsyncServerBuilder {
  private val add_port: add_port_t = new add_port_t {
    def apply(ctx: Ptr[nxt_unit_ctx_t], port: Ptr[nxt_unit_port_t]): CInt = {
      if (port.in_fd != -1) {
        locally {
          val res = fcntl(port.in_fd, F_SETFL, O_NONBLOCK)
          if (res == -1) {
            nxt_unit_warn(ctx, s"fcntl(${port.in_fd}, O_NONBLOCK) failed: ${fromCString(strerror(errno))}, $errno)")
            return -1
          }
        }
        try {
          val poll = Poll(port.in_fd)
          poll.startRead { status =>
            nxt_unit_process_port_msg(ctx, port)
          }
          ctx.data = poll.ptr
          NXT_UNIT_OK
        } catch {
          case NonFatal(e) =>
            nxt_unit_warn(ctx, s"Polling failed: ${fromCString(strerror(errno))}, $errno)")
            NXT_UNIT_ERROR
        }
      } else NXT_UNIT_OK
    }
  }

  private val remove_port: remove_port_t = new remove_port_t {
    def apply(ctx: Ptr[nxt_unit_t], port: Ptr[nxt_unit_port_t]): Unit = {
      val poll = new Poll(port.data)
      poll.stop()
    }
  }

  def apply(): AsyncServerBuilder = new AsyncServerBuilder()
}

class AsyncServerBuilder(
    private val requestHandlers: Seq[Request => Unit],
    private val websocketHandlers: Seq[WSFrame => Unit]
) extends ServerBuilder(requestHandlers, websocketHandlers) {
  def this() = this(Seq.empty, Seq.empty)

  def build(): AsyncServer = {
    val init: Ptr[nxt_unit_init_t] = malloc(sizeof[nxt_unit_init_t]).asInstanceOf[Ptr[nxt_unit_init_t]]
    setBaseHandlers(init)
    init.callbacks.add_port = AsyncServerBuilder.add_port
    init.callbacks.remove_port = AsyncServerBuilder.remove_port
    val ctx: Ptr[nxt_unit_ctx_t] = nxt_unit_init(init)
    if (ctx == null) {
      throw new Exception("Failed to create Unit object")
    }
    new AsyncServer(ctx)
  }

  protected override def create(
      requestHandlers: Seq[Request => Unit],
      websocketHandlers: Seq[WSFrame => Unit]
  ): this.type =
    new AsyncServerBuilder(requestHandlers, websocketHandlers).asInstanceOf[this.type]
}
