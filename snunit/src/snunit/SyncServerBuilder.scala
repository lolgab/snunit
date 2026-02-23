package snunit

import snunit.unsafe.{*, given}

import scala.scalanative.unsafe._

object SyncServerBuilder {
  private val initArray: Array[Byte] = new Array[Byte](sizeof[nxt_unit_init_t].toInt)
  private val init: nxt_unit_init_t_* = {
    initArray.at(0).asInstanceOf[nxt_unit_init_t_*]
  }
  def setRequestHandler(requestHandler: RequestHandler): this.type = {
    ServerBuilder.setRequestHandler(requestHandler)
    this
  }
  def setWebsocketHandler(websocketHandler: WebsocketHandler): this.type = {
    ServerBuilder.setWebsocketHandler(websocketHandler)
    this
  }
  def build(): SyncServer = {
    ServerBuilder.setBaseHandlers(init)
    val ctx: nxt_unit_ctx_t_* = nxt_unit_init(init)
    if (ctx.isNull) {
      throw new Exception("Failed to create Unit object")
    }
    new SyncServerImpl(ctx)
  }
}
