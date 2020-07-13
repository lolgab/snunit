package snunit

import scala.scalanative.unsafe._
import scala.scalanative.libc.stdlib.malloc
import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._
import snunit.unsafe.CApi
import scala.scalanative.loop.Poll
import scala.scalanative.posix.fcntl.{fcntl, F_SETFL, O_NONBLOCK}

object AsyncServerBuilder {
  private val request_handler: request_handler_t = new request_handler_t {
    def apply(req: Ptr[nxt_unit_request_info_t]): Unit = {
      requestHandler(new Request(req))
    }
  }

  private val websocket_handler: websocket_handler_t = new websocket_handler_t {
    def apply(frame: Ptr[nxt_unit_websocket_frame_t]): Unit = {
      websocketHandler(new WSFrame(frame))
    }
  }

  private val quit: quit_t = new quit_t {
    def apply(ctx: Ptr[CApi.nxt_unit_ctx_t]): Unit = {
      nxt_unit_done(ctx)
    }
  }

  private val add_port: add_port_t = new add_port_t {
    def apply(ctx: Ptr[nxt_unit_ctx_t], port: Ptr[nxt_unit_port_t]): CInt = { 
      if(port.in_fd != -1) {
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

  private var requestHandler: Request => Unit = null
  private var websocketHandler: WSFrame => Unit = null

  def withRequestHandler(handler: Request => Unit): AsyncServerBuilder.type = {
    requestHandler = handler
    this
  }
  def withWebsocketHandler(handler: WSFrame => Unit): AsyncServerBuilder.type = {
    websocketHandler = handler
    this
  }

  def build(): AsyncServer = {
    val init: Ptr[nxt_unit_init_t] = malloc(sizeof[nxt_unit_init_t]).asInstanceOf[Ptr[nxt_unit_init_t]]
    init.callbacks.request_handler = request_handler
    init.callbacks.websocket_handler = websocket_handler
    init.callbacks.add_port = add_port
    init.callbacks.remove_port = remove_port
    init.callbacks.quit = quit
    val ctx: Ptr[nxt_unit_ctx_t] = nxt_unit_init(init)
    assert(ctx != null, "nxt_unit_init fail")
    new AsyncServer(ctx)
  }
}
