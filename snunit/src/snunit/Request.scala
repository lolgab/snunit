package snunit

import scala.scalanative.unsafe._
import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._
import snunit.unsafe.Utils._
import snunit.unsafe.nxt_unit_sptr._
import scala.scalanative.runtime.ByteArray

class Request private[snunit] (private val req: Ptr[nxt_unit_request_info_t]) extends AnyVal {
  def method: Method = fromCStringAndSize(req.request.method, req.request.method_length) match {
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

  def send(statusCode: Int, content: String, headers: Seq[(String, String)]): Unit = {
    val fieldsSize: Int = {
      var res = 0
      for ((key, value) <- headers) {
        res += key.length + value.length
      }
      res += content.length
      res
    }

    assert(
      nxt_unit_response_init(req, statusCode.toShort, headers.length, fieldsSize) == 0,
      "nxt_unit_response_init fail"
    )

    for ((key, value) <- headers) {
      req.addField(key, value)
    }
    req.addContent(content)

    nxt_unit_response_send(req)

    nxt_unit_request_done(req, 0)
  }
}
