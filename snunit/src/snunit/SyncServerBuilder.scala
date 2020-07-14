package snunit

import scala.scalanative.unsafe._
import scala.scalanative.libc.stdlib.malloc
import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._

object SyncServerBuilder extends ServerBuilder {
  protected[snunit] val request_handler: request_handler_t = new request_handler_t {
    def apply(req: Ptr[nxt_unit_request_info_t]): Unit = {
      requestHandler(new Request(req))
    }
  }

  protected[snunit] val websocket_handler: websocket_handler_t = new websocket_handler_t {
    def apply(frame: Ptr[nxt_unit_websocket_frame_t]): Unit = {
      websocketHandler(new WSFrame(frame))
    }
  }

  protected[snunit] val quit: quit_t = new quit_t {
    def apply(ctx: Ptr[nxt_unit_ctx_t]): Unit = {
      nxt_unit_done(ctx)
    }
  }

  private var requestHandler: Request => Unit = null
  private var websocketHandler: WSFrame => Unit = null

  def withRequestHandler(handler: Request => Unit): SyncServerBuilder.type = {
    requestHandler = handler
    this
  }

  def withWebsocketHandler(handler: WSFrame => Unit): SyncServerBuilder.type = {
    websocketHandler = handler
    this
  }

  def build(): SyncServer = {
    val init: Ptr[nxt_unit_init_t] = malloc(sizeof[nxt_unit_init_t]).asInstanceOf[Ptr[nxt_unit_init_t]]
    init.callbacks.request_handler = request_handler
    init.callbacks.websocket_handler = websocket_handler
    init.callbacks.quit = quit
    val ctx: Ptr[nxt_unit_ctx_t] = nxt_unit_init(init)
    assert(ctx != null, "nxt_unit_init fail")
    new SyncServer(ctx)
  }
}
