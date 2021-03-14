package io.undertow.server.handlers

import io.undertow.server._

final class BlockingHandler(handler: HttpHandler) extends HttpHandler {
  def handleRequest(exchange: HttpServerExchange): Unit = {
    exchange.startBlocking()
    handler.handleRequest(exchange)
    exchange.endExchange()
  }
}
