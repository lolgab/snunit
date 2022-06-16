package snunit.auth

import snunit._
import sttp.client3._

object Authenticated {
  private val backend = CurlBackend()
  private val vouchBasePath = uri"http://127.0.0.1:9090"
  private val vouchValidatePath = uri"$vouchBasePath/validate"
  private val sttpRequest = basicRequest.get(vouchValidatePath)
  def apply(handler: Handler): Handler = {
    new Handler {
      def handleRequest(req: snunit.Request): Unit = {
        
        println(s"Headers = ${req.headers}")
        var finalRequest = sttpRequest
        var host = ""
        req.headers.foreach {
          case ("Host", h) =>
            host = h
            finalRequest.header("Host", h)
          case ("Cookie", cookie) => finalRequest = finalRequest.header("Cookie", cookie)
          case _                  =>
        }
        val response = finalRequest.send(backend)
        println(s"Vouch response = $response")
        println(s"Vouch code: ${response.code}")
        if (response.code.isSuccess) {
          val vouchUserKey = "X-Vouch-User"
          val vouchUserValue = response.header(vouchUserKey)
          vouchUserValue.foreach(req.addRequestHeader(vouchUserKey, _))
          handler.handleRequest(req)
        } else if (response.code == sttp.model.StatusCode.Unauthorized) {

          val queryString = "?" + (if(req.query.isEmpty) req.query else s"${req.query}&") + "vouch-failcount=&X-Vouch-Token=&error="
          val location = s"http://$host/login?url=http://$host/"
          println(location)
          req.send(
            StatusCode.Found,
            "",
            Seq(("Location", location))
          )
        } else {
          req.send(StatusCode.InternalServerError, "Internal Server Error", Seq.empty)
        }
      }
    }
  }
}
