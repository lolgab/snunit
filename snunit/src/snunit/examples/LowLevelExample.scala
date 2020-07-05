package snunit.examples

import snunit.unsafe.CApi._
import snunit.unsafe.CApiOps._
import scala.scalanative.unsafe._
import snunit.unsafe.CApi
import scala.scalanative.libc.string.memset
object LowLevelExample {
  val request_handler: request_handler_t = new request_handler_t {
    def apply(req: Ptr[CApi.nxt_unit_request_info_t]): Unit = {
      val headers = Seq("Content-Type" -> "text/plain")
      val responseText = "Hello world!\n"

      val fieldsSize: Int =
        headers.foldLeft(0) { case (acc, (a, b)) => acc + a.length + b.length } +
          responseText.length

      assert(nxt_unit_response_init(req, 200, headers.length, fieldsSize) == 0, "nxt_unit_response_init fail")

      for ((key, value) <- headers) {
        req.addField(key, value)
      }
      req.addContent(responseText)

      nxt_unit_response_send(req)

      nxt_unit_request_done(req, 0)
    }
  }

  def main2(args: Array[String]): Unit = {
    val globalZone = Zone.open()
    val init: Ptr[nxt_unit_init_t] = globalZone.alloc(sizeof[nxt_unit_init_t]).asInstanceOf[Ptr[nxt_unit_init_t]]
    init.callbacks.request_handler = request_handler
    val ctx: Ptr[nxt_unit_ctx_t] = nxt_unit_init(init)
    assert(ctx != null, "nxt_unit_init fail")
    nxt_unit_run(ctx)
    nxt_unit_done(ctx)
  }
}
