package snunit

import scala.scalanative.unsafe._
import scala.scalanative.libc.stdlib.malloc
import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._
import scala.scalanative.loop.Poll
import scala.scalanative.posix.fcntl.{fcntl, F_SETFL, O_NONBLOCK}

object AsyncServerBuilder {
  private val add_port: add_port_t = new add_port_t {
    def apply(ctx: Ptr[nxt_unit_ctx_t], port: Ptr[nxt_unit_port_t]): CInt = {
      if (port.in_fd != -1) {
        assert(fcntl(port.in_fd, F_SETFL, O_NONBLOCK) != -1, s"fcntl(${port.in_fd}, F_SETFL, O_NONBLOCK) failed")
        val poll = Poll(port.in_fd)
        poll.start(in = true, out = false) { (status, readable, writable) =>
          nxt_unit_run_once(ctx)
        }
        ctx.data = poll.ptr
      }
      nxt_unit_add_port(ctx, port)
    }
  }

  private val remove_port: remove_port_t = new remove_port_t {
    def apply(ctx: Ptr[nxt_unit_ctx_t], port_id: Ptr[nxt_unit_port_id_t]): Unit = {
      val poll = new Poll(ctx.data)
      poll.stop()
      nxt_unit_remove_port(ctx, port_id)
    }
  }

  def apply(): AsyncServerBuilder = new AsyncServerBuilder()
}

class AsyncServerBuilder(
    private val requestHandlers: Seq[Request => Boolean],
    private val websocketHandlers: Seq[WSFrame => Boolean]
) extends ServerBuilder(requestHandlers, websocketHandlers) {
  def this() = this(Seq.empty, Seq.empty)
  
  def build(): AsyncServer = {
    val init: Ptr[nxt_unit_init_t] = malloc(sizeof[nxt_unit_init_t]).asInstanceOf[Ptr[nxt_unit_init_t]]
    setBaseHandlers(init)
    init.callbacks.add_port = AsyncServerBuilder.add_port
    init.callbacks.remove_port = AsyncServerBuilder.remove_port
    val ctx: Ptr[nxt_unit_ctx_t] = nxt_unit_init(init)
    assert(ctx != null, "nxt_unit_init fail")
    new AsyncServer(ctx)
  }

  protected override def create(
      requestHandlers: Seq[Request => Boolean],
      websocketHandlers: Seq[WSFrame => Boolean]
  ): this.type =
    new AsyncServerBuilder(requestHandlers, websocketHandlers).asInstanceOf[this.type]
}
