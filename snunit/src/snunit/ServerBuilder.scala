package snunit

import scala.scalanative.unsafe._
import snunit.unsafe.PtrUtils
import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._

abstract class ServerBuilder protected (
    private val requestHandlers: Seq[Request => Unit],
    private val websocketHandlers: Seq[WSFrame => Unit]
) {
  def withRequestHandler(handler: Request => Unit): this.type = {
    copy(requestHandlers = requestHandlers :+ handler)
  }
  def withWebsocketHandler(handler: WSFrame => Unit): this.type = {
    copy(websocketHandlers = websocketHandlers :+ handler)
  }
  private def copy(
      requestHandlers: Seq[Request => Unit] = requestHandlers,
      websocketHandlers: Seq[WSFrame => Unit] = websocketHandlers
  ): this.type =
    create(
      requestHandlers = requestHandlers,
      websocketHandlers = websocketHandlers
    ).asInstanceOf[this.type]

  def build(): Server

  protected def create(requestHandlers: Seq[Request => Unit], websocketHandlers: Seq[WSFrame => Unit]): this.type

  protected def setBaseHandlers(init: Ptr[nxt_unit_init_t]): Unit = {
    init.data = PtrUtils.toPtr(this)
    init.callbacks.request_handler = ServerBuilder.request_handler
    init.callbacks.websocket_handler = ServerBuilder.websocket_handler
    init.callbacks.quit = ServerBuilder.quit
  }
}

object ServerBuilder {
  protected[snunit] val request_handler: request_handler_t = new request_handler_t {
    def apply(req: Ptr[nxt_unit_request_info_t]): Unit = {
      val builder = PtrUtils.fromPtr[ServerBuilder](req.unit.data)
      new Request(req).runHandler(builder.requestHandlers)
    }
  }

  protected[snunit] val websocket_handler: websocket_handler_t = new websocket_handler_t {
    def apply(frame: Ptr[nxt_unit_websocket_frame_t]): Unit = {
      val builder = PtrUtils.fromPtr[ServerBuilder](frame.req.unit.data)
      new WSFrame(frame).runHandler(builder.websocketHandlers)
    }
  }

  protected[snunit] val quit: quit_t = new quit_t {
    def apply(ctx: Ptr[nxt_unit_ctx_t]): Unit = {
      nxt_unit_done(ctx)
    }
  }
}
