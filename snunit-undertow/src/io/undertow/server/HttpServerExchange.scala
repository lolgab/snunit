package io.undertow.server

import java.io.InputStream
import java.io.OutputStream

import io.undertow.server.handlers.Cookie
import io.undertow.util.HeaderMap
import snunit._

final class HttpServerExchange private[undertow] (req: Request) {
  private var state = 200
  private var complete = false
  private val responseHeaders = new HeaderMap

  private lazy val outputStream = new java.io.OutputStream {
    override def write(b: Int): Unit = ???

    override def write(b: Array[Byte]): Unit = {
      req.send(new StatusCode(state), b, responseHeaders.asScala)
    }

    override def write(b: Array[Byte], off: Int, len: Int): Unit = ???

    override def flush(): Unit = ???

    override def close(): Unit = ???
  }
  private lazy val inputStream = new java.io.ByteArrayInputStream(req.contentRaw)
  def getRequestHeaders(): HeaderMap = new HeaderMap(req.headers)
  def getResponseHeaders(): HeaderMap = responseHeaders
  def getRequestMethod(): String = req.method.name
  def getInputStream(): InputStream = {
    inputStream
  }
  def getRequestCookies(): java.util.Map[String, Cookie] = {
    println("getRequestCookies")
    ???
  }
  def setResponseCookie(cookie: Cookie): HttpServerExchange = {
    println("setResponseCookie")
    this
  }

  def setStatusCode(statusCode: Int): HttpServerExchange = {
    state = statusCode
    this
  }
  def getQueryParameters(): java.util.Map[String, java.util.Deque[String]] = new java.util.HashMap // TODO
  def getOutputStream(): OutputStream = outputStream
  def isComplete(): Boolean = {
    println(s"calling isComplete: $complete")
    complete
  }
  def getRequestPath(): String = req.path
}
