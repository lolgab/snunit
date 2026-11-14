package snunit

import snunit.unsafe.{*, given}

import scala.scalanative.unsafe._

object SyncServerBuilder {
  private val initArray: Array[Byte] = new Array[Byte](sizeof[nxt_unit_init_t].toInt)
  private val init: nxt_unit_init_t_* = {
    initArray.at(0).asInstanceOf[nxt_unit_init_t_*]
  }

  /** Configures FreeUnit and the standalone executable. See [[snunit.config.UnitConfig]]. */
  def setConfig(config: snunit.config.UnitConfig): this.type = {
    ServerBuilder.setConfig(config)
    this
  }
  def setRequestHandler(requestHandler: RequestHandler): this.type = {
    ServerBuilder.setRequestHandler(requestHandler)
    this
  }
  def setWebsocketHandler(websocketHandler: WebsocketHandler): this.type = {
    ServerBuilder.setWebsocketHandler(websocketHandler)
    this
  }

  /** Maximum number of requests that this process runs at the same time, 0 (the default) for no limit.
    *
    * At the limit the process stops taking requests from FreeUnit: they wait in the queue of the application, where
    * another process can take them, until a request is done. A request that waits longer than the `timeout` of
    * [[snunit.config.Limits]] gets 503. A websocket counts until it is closed. The first requests of a process can go
    * over the limit, by at most one per thread of the router.
    *
    * Useful when the handler answers later from other threads: a handler that answers before returning already runs one
    * request at a time.
    */
  def setConcurrencyLimit(limit: Int): this.type = {
    ConcurrencyLimit.setLimit(limit)
    this
  }
  def build(): SyncServer = {
    ServerBuilder.setBaseHandlers(init)
    init.callbacks.add_port = SyncServerImpl.add_port
    init.callbacks.remove_port = SyncServerImpl.remove_port
    init.callbacks.quit = SyncServerImpl.quit
    val ctx: nxt_unit_ctx_t_* = nxt_unit_init(init)
    if (ctx.isNull) {
      throw new Exception("Failed to create Unit object")
    }
    new SyncServerImpl(ctx)
  }
}
