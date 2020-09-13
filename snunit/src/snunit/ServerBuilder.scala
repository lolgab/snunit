package snunit

import scala.scalanative.unsafe._
import snunit.unsafe.DataUtils
import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._

abstract class ServerBuilder protected (
    private val requestHandlers: Seq[Request => Boolean],
    private val websocketHandlers: Seq[WSFrame => Boolean]
) {
  def withRequestHandler(handler: Request => Boolean): this.type = {
    copy(requestHandlers = requestHandlers :+ handler)
  }
  def withWebsocketHandler(handler: WSFrame => Boolean): this.type = {
    copy(websocketHandlers = websocketHandlers :+ handler)
  }
  private def copy(
      requestHandlers: Seq[Request => Boolean] = requestHandlers,
      websocketHandlers: Seq[WSFrame => Boolean] = websocketHandlers
  ): this.type =
    create(
      requestHandlers = requestHandlers,
      websocketHandlers = websocketHandlers
    ).asInstanceOf[this.type]

  def build(): Server

  protected def create(requestHandlers: Seq[Request => Boolean], websocketHandlers: Seq[WSFrame => Boolean]): this.type

  protected def setBaseHandlers(init: Ptr[nxt_unit_init_t]): Unit = {
    DataUtils.setData(init, this)
    init.callbacks.request_handler = ServerBuilder.request_handler
    init.callbacks.websocket_handler = ServerBuilder.websocket_handler
    init.callbacks.quit = ServerBuilder.quit
  }
}

object ServerBuilder {
  protected[snunit] val request_handler: request_handler_t = new request_handler_t {
    def apply(req: Ptr[nxt_unit_request_info_t]): Unit = {
      val builder = DataUtils.getData[ServerBuilder](req)
      val it = builder.requestHandlers.iterator
      var handled = false
      while (!handled && it.hasNext) {
        handled = it.next().apply(new Request(req))
      }
    }
  }

  protected[snunit] val websocket_handler: websocket_handler_t = new websocket_handler_t {
    def apply(frame: Ptr[nxt_unit_websocket_frame_t]): Unit = {
      val builder = DataUtils.getData[ServerBuilder](frame.req)
      val it = builder.websocketHandlers.iterator
      var handled = false
      while (!handled && it.hasNext) {
        handled = it.next().apply(new WSFrame(frame))
      }
    }
  }

  protected[snunit] val quit: quit_t = new quit_t {
    def apply(ctx: Ptr[nxt_unit_ctx_t]): Unit = {
      nxt_unit_done(ctx)
    }
  }
}
