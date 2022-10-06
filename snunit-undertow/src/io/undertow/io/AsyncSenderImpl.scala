package io.undertow.io

import io.undertow.server.HttpServerExchange

class AsyncSenderImpl(exchange: HttpServerExchange) extends Sender {
  def send(data: String): Unit =
    exchange.req.send(exchange.getStatusCode(), data.getBytes(), exchange.getResponseHeaders().asScala)
}
