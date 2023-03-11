package snunit

import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._

import scala.scalanative.unsafe._

private[snunit] object ServerBuilder {

  private var requestHandler: RequestHandler = new RequestHandler {
    def handleRequest(req: Request) = req.send(500, Array.emptyByteArray, Seq.empty)
  }

  private[snunit] def setRequestHandler(requestHandler: RequestHandler): Unit = {
    this.requestHandler = requestHandler
  }

  private[snunit] def setBaseHandlers(init: Ptr[nxt_unit_init_t]): Unit = {
    init.callbacks.request_handler = ServerBuilder.request_handler
    init.callbacks.quit = ServerBuilder.quit
  }

  protected[snunit] val request_handler: request_handler_t = (req: Ptr[nxt_unit_request_info_t]) => {
    requestHandler.handleRequest(new RequestImpl(req))
  }

  protected[snunit] val quit: quit_t = (ctx: Ptr[nxt_unit_ctx_t]) => {
    nxt_unit_done(ctx)
    ()
  }
}
