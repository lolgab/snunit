package snunit

import scala.collection.mutable
import scala.scalanative.unsafe._
import scala.scalanative.libc.stdlib.malloc
import snunit.unsafe.PtrUtils
import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._

object SyncServerBuilder {
  def apply(): SyncServerBuilder = new SyncServerBuilder()
}
class SyncServerBuilder private (
    private val requestHandlers: Seq[Request => Unit],
    private val websocketHandlers: Seq[WSFrame => Unit]
) extends ServerBuilder(requestHandlers, websocketHandlers) {
  def this() = this(requestHandlers = Seq.empty, websocketHandlers = Seq.empty)

  def build(): SyncServer = {
    val init: Ptr[nxt_unit_init_t] = malloc(sizeof[nxt_unit_init_t]).asInstanceOf[Ptr[nxt_unit_init_t]]
    setBaseHandlers(init)
    val ctx: Ptr[nxt_unit_ctx_t] = nxt_unit_init(init)
    assert(ctx != null, "nxt_unit_init fail")
    new SyncServer(ctx)
  }

  protected override def create(
      requestHandlers: Seq[Request => Unit],
      websocketHandlers: Seq[WSFrame => Unit]
  ): this.type = new SyncServerBuilder(requestHandlers, websocketHandlers).asInstanceOf[this.type]
}
