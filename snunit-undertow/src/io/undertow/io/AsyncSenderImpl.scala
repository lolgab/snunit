package io.undertow.io

import io.undertow.server.HttpServerExchange
import snunit._

class AsyncSenderImpl(exchange: HttpServerExchange) extends Sender {
  def send(data: String): Unit =
    exchange.req.send(StatusCode(exchange.getStatusCode()), data, exchange.getResponseHeaders().asScala)
}
