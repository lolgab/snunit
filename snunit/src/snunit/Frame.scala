package snunit

trait Frame {
  def opcode: Byte
  def rsv3: Byte
  def rsv2: Byte
  def rsv1: Byte
  def fin: Byte
  def request: Request
  def contentRaw: Array[Byte]
  def content: String = new String(contentRaw)
  def sendDone(): Unit
  def send(opcode: Byte, last: Byte, content: Array[Byte]): Unit
}
