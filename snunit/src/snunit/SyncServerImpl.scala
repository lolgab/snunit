package snunit

import snunit.unsafe._

import scala.scalanative.unsafe._

class SyncServerImpl private[snunit] (private val ctx: nxt_unit_ctx_t_*) extends SyncServer {
  def listen(): Unit = {
    nxt_unit_run(ctx)
    nxt_unit_done(ctx)
  }
  def runOnce(): Unit = {
    nxt_unit_run_once(ctx)
  }
  def done(): Unit = {
    nxt_unit_done(ctx)
  }
}
