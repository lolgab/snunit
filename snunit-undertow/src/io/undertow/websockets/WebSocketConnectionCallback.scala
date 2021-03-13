package io.undertow.websockets

import io.undertow.websockets.core.WebSocketChannel
import io.undertow.websockets.spi.WebSocketHttpExchange

trait WebSocketConnectionCallback {
  def onConnect(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit
}
