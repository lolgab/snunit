package snunit

import scala.scalanative.unsafe._

import snunit.unsafe.CApi._

class AsyncServer private[snunit] (private val ctx: Ptr[nxt_unit_ctx_t]) extends AnyVal with Server {}
