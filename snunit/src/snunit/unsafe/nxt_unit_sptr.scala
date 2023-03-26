package snunit.unsafe

import scala.scalanative.unsafe._

type nxt_unit_sptr_t = CInt

// inline
// def nxt_unit_sptr_set(sptr: nxt_unit_sptr_t, ptr: Ptr[Byte]): Unit = {
//   val base: Byte = !sptr.asInstanceOf[Ptr[Byte]]
//   !sptr = (ptr - base).toInt
// }

inline def nxt_unit_sptr_get(sptr: Ptr[nxt_unit_sptr_t]): Ptr[Byte] = {
  (sptr.asInstanceOf[Ptr[Byte]] + !sptr).asInstanceOf[Ptr[Byte]]
}
