package snunit

sealed trait Opcode {
  val value: Byte
}

object Opcode {
  case object Cont extends Opcode { val value: Byte = 0x00 }
  case object Text extends Opcode { val value: Byte = 0x01 }
  case object Binary extends Opcode { val value: Byte = 0x02 }
  case object Close extends Opcode { val value: Byte = 0x08 }
  case object Ping extends Opcode { val value: Byte = 0x09 }
  case object Pong extends Opcode { val value: Byte = 0x0a }
}
