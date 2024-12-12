package io.undertow.server.handlers

import io.undertow.server._
import scala.scalanative.meta.LinktimeInfo

final class BlockingHandler(handler: HttpHandler) extends HttpHandler {
  def handleRequest(exchange: HttpServerExchange): Unit = {
    exchange.startBlocking()
    if (LinktimeInfo.isMultithreadingEnabled) {
      scala.concurrent.ExecutionContext.global.execute(() =>
        try { handler.handleRequest(exchange) }
        finally { exchange.endExchange() }
      )
    } else {
      try { handler.handleRequest(exchange) }
      finally { exchange.endExchange() }
    }
  }
}
