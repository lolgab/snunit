package snunit

import scala.scalanative.runtime.ByteArray
import scala.scalanative.unsafe._

import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._

class WSFrame(val frame: Ptr[nxt_unit_websocket_frame_t]) extends AnyVal {
  def opcode =
    frame.header.opcode match {
      case 0x00 => Opcode.Cont
      case 0x01 => Opcode.Text
      case 0x02 => Opcode.Binary
      case 0x08 => Opcode.Close
      case 0x09 => Opcode.Ping
      case 0x0a => Opcode.Pong
    }

  def read(): Array[Byte] = {
    val length = frame.content_length
    val array = new Array[Byte](length.toInt)

    val res = nxt_unit_websocket_read(frame, array.asInstanceOf[ByteArray].at(0), length)
    assert(res == length)
    array
  }

  def send(opcode: Opcode, data: Array[Byte]) = {
    nxt_unit_websocket_send(frame.req, opcode.value, frame.header.fin, data.asInstanceOf[ByteArray].at(0), data.length)
  }

  def done(): Unit = nxt_unit_websocket_done(frame)

  def close(): Unit = nxt_unit_request_done(frame.req, NXT_UNIT_OK)

  @inline
  private[snunit] def runHandler(handlers: Seq[WSFrame => Unit]) = {
    if (handlers.nonEmpty) {
      frame.req.data = unsafe.PtrUtils.toPtr(handlers.tail)
      handlers.head(new WSFrame(frame))
    }
  }

  def next(): Unit = {
    val handlers = unsafe.PtrUtils.fromPtr[Seq[WSFrame => Unit]](frame.req.data)
    runHandler(handlers)
  }
}
