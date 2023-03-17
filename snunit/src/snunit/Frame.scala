package snunit

import snunit.unsafe.{*, given}

import scala.annotation.targetName
import scala.scalanative.unsafe._

opaque type Frame = Ptr[nxt_unit_websocket_frame_t]

object Frame:
  def apply(frame: Ptr[nxt_unit_websocket_frame_t]): Frame = frame

extension (frame: Frame) {
  inline def opcode: Byte = frame.header.opcodeInternal
  inline def rsv3: Byte = snunit.unsafe.rsv3(frame.header)
  inline def rsv2: Byte = snunit.unsafe.rsv2(frame.header)
  inline def rsv1: Byte = snunit.unsafe.rsv1(frame.header)
  inline def fin: Byte = snunit.unsafe.fin(frame.header)
  @inline def frameRequest: Request = Request(frame.req)
  def frameContentRaw(): Array[Byte] = {
    val contentLength = frame.content_length
    if (contentLength > 0) {
      val array = new Array[Byte](contentLength.toInt)

      nxt_unit_websocket_read(frame, array.at(0), contentLength)
      array
    } else Array.emptyByteArray
  }
  def frameContent: String = new String(frameContentRaw())

  inline def sendFrameDone(): Unit = {
    nxt_unit_websocket_done(frame)
  }

  @inline def sendFrame(opcode: Byte, last: Byte, content: Array[Byte]): Unit =
    nxt_unit_websocket_send(frame.req, opcode, last, content.at(0), content.length)
}
