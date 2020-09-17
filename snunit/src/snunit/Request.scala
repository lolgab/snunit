package snunit

import scala.scalanative.runtime.ByteArray
import scala.scalanative.unsafe._

import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._
import snunit.unsafe.Utils._
import snunit.unsafe.nxt_unit_sptr._

class Request private[snunit] (private val req: Ptr[nxt_unit_request_info_t]) extends AnyVal {
  def method: Method =
    fromCStringAndSize(req.request.method, req.request.method_length) match {
      case "GET"     => Method.GET
      case "HEAD"    => Method.HEAD
      case "POST"    => Method.POST
      case "PUT"     => Method.PUT
      case "DELETE"  => Method.DELETE
      case "CONNECT" => Method.CONNECT
      case "OPTIONS" => Method.OPTIONS
      case "TRACE"   => Method.TRACE
      case "PATCH"   => Method.PATCH
      case other     => new Method(other)
    }

  def content: String = {
    val contentLength = req.request.content_length
    if (contentLength > 0) {
      val array = new Array[Byte](contentLength.toInt)

      nxt_unit_request_read(req, array.asInstanceOf[ByteArray].at(0), contentLength)
      new String(array)
    } else ""
  }

  def path: String = fromCStringAndSize(req.request.path, req.request.path_length)

  // def target: String = fromCStringAndSize(req.request.target, req.request.target_length)

  private def addField(name: String, value: String): Unit = {
    val n = name.getBytes().asInstanceOf[ByteArray]
    val v = value.getBytes().asInstanceOf[ByteArray]
    val res = nxt_unit_response_add_field(req, n.at(0), n.length.toByte, v.at(0), v.length)
    if (res != 0) throw new Exception()
  }

  private def addContent(content: String): Unit = {
    val v = content.getBytes().asInstanceOf[ByteArray]
    val res = nxt_unit_response_add_content(req, v.at(0), v.length)
    if (res != 0) throw new Exception()
  }

  @inline
  private[snunit] def runHandler(handlers: Seq[Request => Unit]) = {
    if (handlers.nonEmpty) {
      req.data = unsafe.PtrUtils.toPtr(handlers.tail)
      handlers.head(new Request(req))
    }
  }

  def next(): Unit = {
    val handlers = unsafe.PtrUtils.fromPtr[Seq[Request => Unit]](req.data)
    runHandler(handlers)
  }

  def send(statusCode: Int, content: String, headers: Seq[(String, String)]): Unit = {
    val fieldsSize: Int = {
      var res = 0
      for ((key, value) <- headers) {
        res += key.length + value.length
      }
      res += content.length
      res
    }

    locally {
      val res = nxt_unit_response_init(req, statusCode.toShort, headers.length, fieldsSize)
      if (res != NXT_UNIT_OK) throw new Exception("Failed to create response")
    }

    for ((key, value) <- headers) {
      addField(key, value)
    }
    addContent(content)

    locally {
      val res = nxt_unit_response_send(req)
      if (res != NXT_UNIT_OK) throw new Exception("Failed to send response")
    }

    nxt_unit_request_done(req, 0)
  }
}
