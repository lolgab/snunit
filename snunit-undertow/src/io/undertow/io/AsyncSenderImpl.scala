package io.undertow.io

import io.undertow.server.HttpServerExchange
import snunit.*

class AsyncSenderImpl(exchange: HttpServerExchange) extends Sender {
  def send(data: String): Unit =
    exchange.req.send(StatusCode(exchange.getStatusCode()), data, Headers(exchange.getResponseHeaders().asScala: _*))
}
