package snunit

import snunit.unsafe._

import scala.scalanative.unsafe._

class AsyncServer private[snunit] (private val ctx: nxt_unit_ctx_t_*) extends Server
