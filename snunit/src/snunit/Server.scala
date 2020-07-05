package snunit

import scala.scalanative.unsafe._
import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._

class Server private[snunit] (private val ctx: Ptr[nxt_unit_ctx_t]) extends AnyVal {
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
