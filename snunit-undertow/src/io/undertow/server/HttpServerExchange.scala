package io.undertow.server

import io.undertow.io.AsyncSenderImpl
import io.undertow.io.BlockingSenderImpl
import io.undertow.io.Sender
import io.undertow.server.handlers.Cookie
import io.undertow.util.HeaderMap
import snunit._

import java.io.InputStream
import java.io.OutputStream

final class HttpServerExchange private[undertow] (private[undertow] val req: Request) {
  private var state = 200

  private val responseHeaders = new HeaderMap
  private var blockingHttpExchange: BlockingHttpExchange = null
  private var sender: Sender = null

  def getRequestHeaders(): HeaderMap = new HeaderMap(req.headers.toMap)
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
  def getStatusCode(): Int = {
    state // TODO: Apply bit mask
  }
  def setStatusCode(statusCode: Int): HttpServerExchange = {
    state = statusCode
    this
  }
  def getResponseSender(): Sender = {
    if (blockingHttpExchange != null) {
      blockingHttpExchange.getSender()
    } else if (sender != null) {
      sender
    } else {
      sender = new AsyncSenderImpl(this)
      sender
    }
  }
  def getQueryParameters(): java.util.Map[String, java.util.Deque[String]] = {
    val result = new java.util.HashMap[String, java.util.Deque[String]]
    def add(key: String, value: String) = result.get(key) match {
      case null =>
        val list = new java.util.LinkedList[String]
        list.add(value)
        result.put(key, list)
      case list =>
        list.add(value)
    }
    req.query.split('&').foreach {
      case s"$key=$value" =>
        add(key, value)
      case "" =>
      case key =>
        add(key, "")
    }
    result
  }
  def getOutputStream(): OutputStream = blockingHttpExchange.getOutputStream()
  def isComplete(): Boolean = ???
  def getRequestURI(): String = req.target.split('?')(0)
  def getRequestPath(): String = req.path
  def startBlocking(): BlockingHttpExchange = {
    val old = blockingHttpExchange
    blockingHttpExchange = new HttpServerExchange.DefaultBlockingHttpExchange(this)
    old
  }
  def endExchange(): HttpServerExchange = {
    if (blockingHttpExchange != null) {
      blockingHttpExchange.close()
    }
    this
  }
}

object HttpServerExchange {
  private class DefaultBlockingHttpExchange(exchange: HttpServerExchange) extends BlockingHttpExchange {
    private var inputStream: InputStream = null
    private var outputStream: OutputStream = null
    private var sender: Sender = null

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
    override def getSender(): Sender = {
      if (sender == null) {
        sender = new BlockingSenderImpl(exchange, getOutputStream())
      }
      sender
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
