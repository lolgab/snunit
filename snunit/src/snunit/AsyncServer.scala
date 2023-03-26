package snunit

import snunit.unsafe._

import scala.scalanative.unsafe._

class AsyncServer private[snunit] (private val ctx: Ptr[nxt_unit_ctx_t]) extends Server {}
