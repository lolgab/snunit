package snunit

import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._
import snunit.unsafe.Utils._

import scala.scalanative.runtime.ByteArray
import scala.scalanative.unsafe._

class RequestImpl private[snunit] (private val req: Ptr[nxt_unit_request_info_t]) extends Request {
  def method: Method = MethodUtils.of(req.request.method, req.request.method_length)

  def headers: Seq[(String, String)] = {
    for (i <- 0 until req.request.fields_count) yield {
      val field = req.request.fields + i
      val fieldName = fromCStringAndSize(field.name, field.name_length)
      val fieldValue = fromCStringAndSize(field.value, field.value_length)
      fieldName -> fieldValue
    }
  }

  lazy val contentRaw: Array[Byte] = {
    val contentLength = req.request.content_length
    if (contentLength > 0) {
      val array = new Array[Byte](contentLength.toInt)

      nxt_unit_request_read(req, array.asInstanceOf[ByteArray].at(0), contentLength)
      array
    } else Array.emptyByteArray
  }

  def path: String = fromCStringAndSize(req.request.path, req.request.path_length)

  def query: String = fromCStringAndSize(req.request.query, req.request.query_length)

  private def addHeader(name: String, value: String): Unit = {
    val n = name.getBytes().asInstanceOf[ByteArray]
    val v = value.getBytes().asInstanceOf[ByteArray]
    val res = nxt_unit_response_add_field(req, n.at(0), n.length.toByte, v.at(0), v.length)
    if (res != 0) throw new Exception("Failed to add field")
  }

  @inline
  private def startSendUnsafe(statusCode: StatusCode, headers: Seq[(String, String)], contentLength: Int): Unit = {
    val fieldsSize: Int = {
      var res = 0
      for ((key, value) <- headers) {
        res += key.length + value.length
      }
      res
    }

    locally {
      val res = nxt_unit_response_init(req, statusCode.value.toShort, headers.length, fieldsSize + contentLength)
      if (res != NXT_UNIT_OK) throw new Exception("Failed to create response")
    }

    for ((key, value) <- headers) {
      addHeader(key, value)
    }
  }
  @inline
  def startSend(statusCode: StatusCode, headers: Seq[(String, String)]): Unit = startSendUnsafe(statusCode, headers, 0)
  def sendByte(byte: Int): Unit = {
    val bytePtr = stackalloc[Byte]()
    !bytePtr = byte.toByte
    val res = nxt_unit_response_write_nb(
      req,
      bytePtr,
      1L,
      0L
    )
    if (res < 0) {
      throw new Exception("Failed to send byte")
    }
  }
  @inline
  private def sendBatchUnsafe(data: Array[Byte], off: Int, len: Int): Unit = {
    val res = nxt_unit_response_write_nb(
      req,
      if (len > 0) data.asInstanceOf[ByteArray].at(off) else null,
      len,
      0L
    )
    if (res < 0) {
      throw new Exception("Failed to send batch")
    }
  }
  def sendBatch(data: Array[Byte], off: Int, len: Int): Unit = {
    if (off < 0 || len < 0 || len > data.length - off) {
      throw new IndexOutOfBoundsException
    }
    sendBatchUnsafe(data, off, len)
  }
  @inline
  def sendBatch(data: Array[Byte]): Unit = {
    sendBatchUnsafe(data, 0, data.length)
  }
  @inline
  def sendDone(): Unit = {
    nxt_unit_request_done(req, NXT_UNIT_OK)
  }
  def send(statusCode: StatusCode, content: Array[Byte], headers: Seq[(String, String)]): Unit = {
    val byteArray = content.asInstanceOf[ByteArray]
    val contentLength = byteArray.length
    startSendUnsafe(statusCode, headers, contentLength)
    if (contentLength > 0) {
      nxt_unit_response_add_content(req, byteArray.at(0), contentLength)
    }
    nxt_unit_response_send(req)
    sendDone()
  }
  def outputStream = new java.io.OutputStream {
    override def write(b: Int): Unit = sendByte(b)
    override def write(b: Array[Byte]): Unit = sendBatch(b)
    override def write(b: Array[Byte], off: Int, len: Int): Unit = sendBatch(b, off, len)
  }
}
