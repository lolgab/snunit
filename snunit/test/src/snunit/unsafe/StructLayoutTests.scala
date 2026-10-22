package snunit.unsafe

import utest._
import scala.scalanative.unsafe.*

// Mirrors of the C structs of nxt_unit.h: a wrong size shifts every field that follows
object StructLayoutTests extends TestSuite {
  val tests = Tests {
    // 12 function pointers, including ready_handler
    test("nxt_unit_callbacks_t") {
      sizeof[nxt_unit_callbacks_t].toInt ==> 96
    }
    // The callbacks are embedded by value in nxt_unit_init_t
    test("nxt_unit_init_t") {
      sizeof[nxt_unit_init_t].toInt ==> 248
    }
  }
}
