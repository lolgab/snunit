package snunit

import snunit.unsafe.{*, given}

import scala.annotation.targetName
import scala.collection.immutable.ArraySeq
import scala.scalanative.unsafe.*

opaque type Request = nxt_unit_request_info_t_*

object Request:
  def apply(req: nxt_unit_request_info_t_*): Request = req

extension (req: Request) {
  def method: snunit.Method =
    methodOf(snunit.unsafe.method(req.request), req.request.method_length)
  def version: String =
    versionOf(snunit.unsafe.version(req.request), req.request.version_length)

  def headers: Headers = {
    val headers = Headers(req.request.fields_count)
    var i = 0
    while (i < req.request.fields_count) {
      val field = req.request.fields(i)
      val fieldName = fromCStringAndSize(field.name, field.name_length)
      val fieldValue = fromCStringAndSize(field.value, field.value_length)
      headers.updateName(i, fieldName)
      headers.updateValue(i, fieldValue)
      i += 1
    }
    headers
  }

  @inline def headersLength: Int = req.request.fields_count
  private inline def checkIndex(index: Int): Unit = {
    if (index < 0 && index >= req.request.fields_count)
      throw new IndexOutOfBoundsException(s"Index $index out of bounds for length ${req.request.fields_count}")
  }
  def headerName(index: Int): String = {
    checkIndex(index)
    headerNameUnsafe(index)
  }
  @inline def headerNameUnsafe(index: Int): String = {
    val field = req.request.fields(index)
    fromCStringAndSize(field.name, field.name_length)
  }
  def headerValue(index: Int): String = {
    checkIndex(index)
    headerValueUnsafe(index)
  }
  @inline def headerValueUnsafe(index: Int): String = {
    val field = req.request.fields(index)
    fromCStringAndSize(field.value, field.value_length)
  }
  // TODO: should be a lazy val
  def contentRaw(): Array[Byte] = {
    val contentLength = req.request.content_length
    if (contentLength > 0) {
      val array = new Array[Byte](contentLength.toInt)

      nxt_unit_request_read(req, array.at(0), contentLength)
      array
    } else Array.emptyByteArray
  }

  def target: String = fromCStringAndSize(snunit.unsafe.target(req.request), req.request.target_length)

  def path: String = fromCStringAndSize(snunit.unsafe.path(req.request), req.request.path_length)

  def query: String = fromCStringAndSize(snunit.unsafe.query(req.request), req.request.query_length)

  private inline def addHeader(name: String, value: String): Unit = {
    val n = stringBytes(name)
    val v = stringBytes(value)
    val res = nxt_unit_response_add_field(req, n.pointer, n.contentLength.toByte, v.pointer, v.contentLength)
    if (res != 0) throw new Exception("Failed to add field")
  }

  private inline def startSendUnsafe(
      statusCode: StatusCode,
      headers: Headers,
      contentLength: Int
  ): Unit = {
    locally {
      val res = nxt_unit_response_init(req, statusCode, headers.length, headers.fieldsLength + contentLength)
      if (res != NXT_UNIT_OK) throw new Exception("Failed to create response")
    }

    headers.foreach((name, value) => addHeader(name, value))
  }
  inline def startSend(statusCode: StatusCode, headers: Headers): Unit =
    startSendUnsafe(statusCode, headers, 0)
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
  private inline def sendBatchUnsafe(inline data: Ptr[Byte], inline len: Int): Unit = {
    val res = nxt_unit_response_write_nb(
      req,
      data,
      len,
      0L
    )
    if (res < 0) {
      throw new Exception("Failed to send batch")
    }
  }
  private inline def sendBatchUnsafe(data: Array[Byte], off: Int, len: Int): Unit = {
    val res = nxt_unit_response_write_nb(
      req,
      data.at(off),
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

  inline def sendBatch(data: Array[Byte]): Unit = {
    sendBatchUnsafe(data, 0, data.length)
  }

  inline def sendDone(): Unit = {
    nxt_unit_request_done(req, NXT_UNIT_OK)
  }
  @targetName("send_array")
  def send(statusCode: StatusCode, content: Array[Byte], headers: Headers): Unit = {
    val byteArray = content
    val contentLength = byteArray.length
    startSendUnsafe(statusCode, headers, contentLength)
    if (contentLength > 0) {
      nxt_unit_response_add_content(req, byteArray.at(0), contentLength)
    }
    nxt_unit_response_send(req)
    sendDone()
  }
  def outputStream = new java.io.OutputStream {
    override def write(b: Int): Unit = req.sendByte(b)
    override def write(b: Array[Byte]): Unit = req.sendBatch(b)
    override def write(b: Array[Byte], off: Int, len: Int): Unit = req.sendBatch(b, off, len)
  }

  inline def isWebsocketHandshake: Boolean = nxt_unit_request_is_websocket_handshake(req) != 0
  def upgrade(): Unit = {
    locally {
      val res = nxt_unit_response_init(req, 101, 0, 0)
      if (res != NXT_UNIT_OK) throw new Exception("Failed to create response")
    }

    locally {
      val res = nxt_unit_response_upgrade(req)
      if (res != 0) throw new Exception("Failed to upgrade connection")
    }

    nxt_unit_response_send(req)
  }
  @targetName("request_content")
  def content(): String = new String(contentRaw())

  @targetName("send_string")
  inline def send(statusCode: StatusCode, content: String, headers: Headers): Unit = {
    startSendUnsafe(statusCode, headers, 0)
    readStringBytesWith(content) { buffer =>
      val length = buffer.contentLength
      if (length > 0) {
        sendBatchUnsafe(buffer.pointer, length)
      }
    }
    sendDone()
  }
}
