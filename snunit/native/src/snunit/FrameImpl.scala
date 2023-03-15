package snunit

import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._

import scala.scalanative.unsafe._

class FrameImpl private[snunit] (private val frame: Ptr[nxt_unit_websocket_frame_t]) extends Frame {
  def opcode: Byte = frame.header.opcode
  def rsv3: Byte = frame.header.rsv3
  def rsv2: Byte = frame.header.rsv2
  def rsv1: Byte = frame.header.rsv1
  def fin: Byte = frame.header.fin
  def request: Request = new RequestImpl(frame.req)
  lazy val contentRaw: Array[Byte] = {
    val contentLength = frame.content_length
    if (contentLength > 0) {
      val array = new Array[Byte](contentLength.toInt)

      nxt_unit_websocket_read(frame, array.at(0), contentLength)
      array
    } else Array.emptyByteArray
  }
  @inline
  def sendDone(): Unit = {
    nxt_unit_websocket_done(frame)
  }
  def send(opcode: Byte, last: Byte, content: Array[Byte]): Unit =
    nxt_unit_websocket_send(frame.req, opcode, last, content.at(0), content.length)
}
