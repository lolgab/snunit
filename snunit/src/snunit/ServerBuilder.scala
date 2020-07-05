package snunit

import scala.scalanative.unsafe._
import scala.scalanative.libc.stdlib.malloc
import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._

object ServerBuilder {
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

  private var requestHandler: Request => Unit = null
  private var websocketHandler: WSFrame => Unit = null

  def withRequestHandler(handler: Request => Unit): ServerBuilder.type = {
    requestHandler = handler
    this
  }
  def withWebsocketHandler(handler: WSFrame => Unit): ServerBuilder.type = {
    websocketHandler = handler
    this
  }

  def build(): Server = {
    val init: Ptr[nxt_unit_init_t] = malloc(sizeof[nxt_unit_init_t]).asInstanceOf[Ptr[nxt_unit_init_t]]
    init.callbacks.request_handler = request_handler
    init.callbacks.websocket_handler = websocket_handler
    val ctx: Ptr[nxt_unit_ctx_t] = nxt_unit_init(init)
    assert(ctx != null, "nxt_unit_init fail")
    new Server(ctx)
  }
}
