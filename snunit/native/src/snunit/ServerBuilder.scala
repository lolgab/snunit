package snunit

import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._

import scala.scalanative.unsafe._

private[snunit] object ServerBuilder {

  private var handler: Handler = null

  private[snunit] def setBaseHandlers(init: Ptr[nxt_unit_init_t], handler: Handler): Unit = {
    this.handler = handler
    init.callbacks.request_handler = ServerBuilder.request_handler
    init.callbacks.quit = ServerBuilder.quit
  }

  protected[snunit] val request_handler: request_handler_t = (req: Ptr[nxt_unit_request_info_t]) => {
    handler.handleRequest(new RequestImpl(req))
  }

  protected[snunit] val quit: quit_t = (ctx: Ptr[nxt_unit_ctx_t]) => {
    nxt_unit_done(ctx)
    ()
  }
}
