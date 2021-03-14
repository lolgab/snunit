package io.undertow

import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.websockets.WebSocketProtocolHandshakeHandler

object Handlers {
  def websocket(sessionHandler: WebSocketConnectionCallback): WebSocketProtocolHandshakeHandler = ???
}
