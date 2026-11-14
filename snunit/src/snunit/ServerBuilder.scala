package snunit

import snunit.unsafe.{*, given}

import scala.scalanative.unsafe.*

private[snunit] object ServerBuilder {

  private var requestHandler: RequestHandler = new RequestHandler {
    def handleRequest(req: Request) =
      send(req)(StatusCode.InternalServerError, Array.emptyByteArray, Headers.empty)
  }

  private var websocketHandler: WebsocketHandler = null

  private var config: snunit.config.UnitConfig = snunit.config.UnitConfig()

  private[snunit] def setConfig(config: snunit.config.UnitConfig): Unit = {
    this.config = config
  }

  private[snunit] def setRequestHandler(requestHandler: RequestHandler): Unit = {
    this.requestHandler = requestHandler
  }

  private[snunit] def setWebsocketHandler(websocketHandler: WebsocketHandler): Unit = {
    this.websocketHandler = websocketHandler
  }

  private[snunit] def setBaseHandlers(init: nxt_unit_init_t_*): Unit = {
    snunit.launcher.Launcher.runIfNeeded(config)
    init.callbacks.request_handler = request_handler
    init.callbacks.websocket_handler = websocket_handler
    init.callbacks.close_handler = close_handler
  }

  protected[snunit] val request_handler: request_handler_t = request_handler_t { (req: nxt_unit_request_info_t_*) =>
    ConcurrencyLimit.acquire(req)
    requestHandler.handleRequest(Request(req))
  }

  // What libunit does without a close handler, and the request doesn't count for the concurrency limit anymore
  protected[snunit] val close_handler: close_handler_t = close_handler_t { (req: nxt_unit_request_info_t_*) =>
    nxt_unit_request_done(req, NXT_UNIT_ERROR)
    ConcurrencyLimit.release(req)
  }

  protected[snunit] val websocket_handler: websocket_handler_t = websocket_handler_t {
    (frame: nxt_unit_websocket_frame_t_*) =>
      websocketHandler.handleFrame(Frame(frame))
  }
}
