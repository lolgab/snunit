package snunit

import snunit.unsafe.CApi._

import scala.scalanative.unsafe._

class AsyncServer private[snunit] (private val ctx: Ptr[nxt_unit_ctx_t]) extends AnyVal with Server {}
