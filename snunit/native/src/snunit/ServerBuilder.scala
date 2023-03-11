package snunit

import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._

import scala.scalanative.unsafe._

private[snunit] object ServerBuilder {

  private var requestHandler: RequestHandler = new RequestHandler {
    def handleRequest(req: Request) = req.send(500, Array.emptyByteArray, Seq.empty)
  }

  private var websocketHandler: WebsocketHandler = null

  private[snunit] def setRequestHandler(requestHandler: RequestHandler): Unit = {
    this.requestHandler = requestHandler
  }

  private[snunit] def setWebsocketHandler(websocketHandler: WebsocketHandler): Unit = {
    this.websocketHandler = websocketHandler
  }

  private[snunit] def setBaseHandlers(init: Ptr[nxt_unit_init_t]): Unit = {
    init.callbacks.request_handler = request_handler
    init.callbacks.websocket_handler = websocket_handler
    init.callbacks.quit = quit
  }

  protected[snunit] val request_handler: request_handler_t = (req: Ptr[nxt_unit_request_info_t]) => {
    requestHandler.handleRequest(new RequestImpl(req))
  }

  protected[snunit] val websocket_handler: websocket_handler_t = (frame: Ptr[nxt_unit_websocket_frame_t]) => {
    websocketHandler.handleFrame(new FrameImpl(frame))
  }

  protected[snunit] val quit: quit_t = (ctx: Ptr[nxt_unit_ctx_t]) => {
    nxt_unit_done(ctx)
    ()
  }
}
