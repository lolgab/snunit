package io.undertow.server

import java.io.InputStream
import java.io.OutputStream

import io.undertow.server.handlers.Cookie
import io.undertow.util.HeaderMap
import snunit._

final class HttpServerExchange private[undertow] (private[server] val req: Request) {
  private var state = 200
  private var complete = false
  private val responseHeaders = new HeaderMap
  private var blockingHttpExchange: BlockingHttpExchange = null

  def getRequestHeaders(): HeaderMap = new HeaderMap(req.headers)
  def getResponseHeaders(): HeaderMap = responseHeaders
  def getRequestMethod(): String = req.method.name
  def getInputStream(): InputStream = {
    blockingHttpExchange.getInputStream()
  }
  def getRequestCookies(): java.util.Map[String, Cookie] = {
    ???
  }
  def setResponseCookie(cookie: Cookie): HttpServerExchange = {
    ???
  }

  def setStatusCode(statusCode: Int): HttpServerExchange = {
    state = statusCode
    this
  }
  def getQueryParameters(): java.util.Map[String, java.util.Deque[String]] = new java.util.HashMap // TODO
  def getOutputStream(): OutputStream = blockingHttpExchange.getOutputStream()
  def isComplete(): Boolean = ???
  def getRequestPath(): String = req.path
  def startBlocking(): BlockingHttpExchange = {
    val old = blockingHttpExchange
    blockingHttpExchange = new HttpServerExchange.DefaultBlockingHttpExchange(this)
    old
  }
  def endExchange(): HttpServerExchange = {
    if(blockingHttpExchange != null) {
      blockingHttpExchange.close()
    }
    this
  }
}

object HttpServerExchange {
  private class DefaultBlockingHttpExchange(exchange: HttpServerExchange) extends BlockingHttpExchange {
    private var inputStream: InputStream = null
    private var outputStream: OutputStream = null
    
    def getInputStream(): InputStream = {
      if (inputStream == null) {
        inputStream = new java.io.ByteArrayInputStream(exchange.req.contentRaw)
      }
      inputStream
    }
    def getOutputStream(): OutputStream = {
      if (outputStream == null) {
        outputStream = new java.io.OutputStream {
          private var responseData: Array[Byte] = Array.emptyByteArray

          override def write(b: Int): Unit = ???

          override def write(b: Array[Byte]): Unit = {
            responseData = b
          }

          override def write(b: Array[Byte], off: Int, len: Int): Unit = ???

          override def flush(): Unit = ???

          override def close(): Unit = {
            exchange.req.send(new StatusCode(exchange.state), responseData, exchange.responseHeaders.asScala)
          }
        }
      }
      outputStream
    }
    def close(): Unit = {
      try {
        getInputStream().close()
      } finally {
        getOutputStream().close()
      }
    }
  }

}
