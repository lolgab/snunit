package snunit

import snunit.unsafe.CApi._

import scala.scalanative.runtime.ByteArray
import scala.scalanative.unsafe._

object SyncServerBuilder {
  private val initArray: Array[Byte] = new Array[Byte](sizeof[nxt_unit_init_t].toInt)
  private val init: Ptr[nxt_unit_init_t] = {
    initArray.asInstanceOf[ByteArray].at(0).asInstanceOf[Ptr[nxt_unit_init_t]]
  }
  def build(handler: Handler): SyncServer = {
    ServerBuilder.setBaseHandlers(init, handler)
    val ctx: Ptr[nxt_unit_ctx_t] = nxt_unit_init(init)
    if (ctx == null) {
      throw new Exception("Failed to create Unit object")
    }
    new SyncServerImpl(ctx)
  }
}
