package snunit

import scala.concurrent.ExecutionContext
import scala.scalanative.runtime.ByteArray
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._
import snunit.unsafe.Utils._

class Request private[snunit] (private val req: Ptr[nxt_unit_request_info_t]) extends geny.Readable {
  private var nextRequested: Boolean = false

  def method: Method =
    fromCStringAndSize(req.request.method, req.request.method_length) match {
      case "GET"     => Method.GET
      case "POST"    => Method.POST
      case "PUT"     => Method.PUT
      case "DELETE"  => Method.DELETE
      case "PATCH"   => Method.PATCH
      case "HEAD"    => Method.HEAD
      case "CONNECT" => Method.CONNECT
      case "OPTIONS" => Method.OPTIONS
      case "TRACE"   => Method.TRACE
      case other     => new Method(other)
    }

  lazy val headers: Map[String, String] = {
    val builder = Map.newBuilder[String, String]
    for (i <- 0 until req.request.fields_count) {
      val field = req.request.fields + i
      val fieldName = fromCStringAndSize(field.name, field.name_length)
      val fieldValue = fromCStringAndSize(field.value, field.value_length)
      builder += fieldName -> fieldValue
    }
    builder.result()
  }

  def content: String = new String(contentRaw)

  lazy val contentRaw: Array[Byte] = {
    val contentLength = req.request.content_length
    if (contentLength > 0.toULong) {
      val array = new Array[Byte](contentLength.toInt)

      nxt_unit_request_read(req, array.asInstanceOf[ByteArray].at(0), contentLength)
      array
    } else Array.emptyByteArray
  }

  def path: String = fromCStringAndSize(req.request.path, req.request.path_length)

  private def addHeader(name: String, value: String): Unit = {
    val n = name.getBytes().asInstanceOf[ByteArray]
    val v = value.getBytes().asInstanceOf[ByteArray]
    val res = nxt_unit_response_add_field(req, n.at(0), n.length.toByte, v.at(0), v.length)
    if (res != 0) throw new Exception("Failed to add field")
  }

  @inline
  private[snunit] def runHandler(handlers: Seq[Request => Unit]) = {
    if (handlers.nonEmpty) {
      req.data = unsafe.PtrUtils.toPtr(handlers.tail)
      handlers.head(new Request(req))
    }
  }

  def next(): Unit = {
    nextRequested = true
    ExecutionContext.global.execute {
      new Runnable {
        def run(): Unit = {
          nextRequested = false
          val handlers = unsafe.PtrUtils.fromPtr[Seq[Request => Unit]](req.data)
          runHandler(handlers)
        }
      }
    }
  }
  def withFilter(f: => Unit): Request = {
    if (!nextRequested) {
      f
    }
    this
  }
  def withMethod(method: Method): Request = withFilter {
    if (this.method != method) next()
  }

  def withPath(path: String): Request = withFilter {
    if (this.path != path) next()
  }

  def apply(h: Request => Unit): Unit = withFilter {
    h(this)
  }
  def startSend(statusCode: StatusCode, headers: Seq[(String, String)]): Unit = {
    val fieldsSize: Int = {
      var res = 0
      for ((key, value) <- headers) {
        res += key.length + value.length
      }
      res
    }

    locally {
      val res = nxt_unit_response_init(req, statusCode.value.toShort, headers.length, fieldsSize)
      if (res != NXT_UNIT_OK) throw new Exception("Failed to create response")
    }

    for ((key, value) <- headers) {
      addHeader(key, value)
    }
  }
  def sendByte(byte: Int): Unit = {
    val bytePtr = stackalloc[Byte]
    !bytePtr = byte.toByte
    val res = nxt_unit_response_write_nb(
      req,
      bytePtr,
      1L.toULong,
      0L.toULong
    )
    if (res < 0.toULong) {
      throw new Exception("Failed to send byte")
    }
  }
  @inline
  private def sendBatchUnsafe(data: Array[Byte], off: Int, len: Int): Unit = {
    val res = nxt_unit_response_write_nb(
      req,
      if (len > 0) data.asInstanceOf[ByteArray].at(off) else null,
      len.toULong,
      0L.toULong
    )
    if (res < 0.toULong) {
      throw new Exception("Failed to send batch")
    }
  }
  def sendBatch(data: Array[Byte], off: Int, len: Int): Unit = {
    if (off < 0 || len < 0 || len > data.length - off) {
      throw new IndexOutOfBoundsException
    }
    sendBatchUnsafe(data, off, len)
  }
  def sendBatch(data: Array[Byte]): Unit = {
    sendBatchUnsafe(data, 0, data.length)
  }
  def sendDone(): Unit = {
    nxt_unit_request_done(req, NXT_UNIT_OK)
  }
  def sendRaw(statusCode: StatusCode, contentRaw: Array[Byte], headers: Seq[(String, String)]): Unit = {
    startSend(statusCode, headers)
    sendBatch(contentRaw)
    sendDone()
  }
  def send(statusCode: StatusCode, content: String, headers: Seq[(String, String)]): Unit = {
    sendRaw(statusCode, content.getBytes(), headers)
  }
  def readBytesThrough[T](f: java.io.InputStream => T) = f(new java.io.ByteArrayInputStream(contentRaw))
  override def httpContentType: Option[String] = headers.get("Content-Type")
  override def contentLength: Option[Long] = Some(req.request.content_length.toLong)
  lazy val outputStream = new java.io.OutputStream {
    override def write(b: Int): Unit = sendByte(b)
    override def write(b: Array[Byte]): Unit = sendBatch(b)
    override def write(b: Array[Byte], off: Int, len: Int): Unit = sendBatch(b, off, len)
  }
  def send(statusCode: StatusCode, content: geny.Writable, headers: Seq[(String, String)]): Unit = {
    startSend(
      statusCode,
      headers ++
        content.contentLength.map("Content-Length" -> _.toString) ++
        content.httpContentType.map(
          "Content-Type" -> _
        )
    )
    content.writeBytesTo(outputStream)
    sendDone()
  }
}
