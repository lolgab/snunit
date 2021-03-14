package io.undertow.server

trait HttpHandler {
  def handleRequest(exchange: HttpServerExchange): Unit
}
