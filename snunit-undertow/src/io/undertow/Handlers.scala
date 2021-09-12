package io.undertow

import _root_.io.undertow.websockets.WebSocketConnectionCallback
import _root_.io.undertow.websockets.WebSocketProtocolHandshakeHandler

object Handlers {
  def websocket(sessionHandler: WebSocketConnectionCallback): WebSocketProtocolHandshakeHandler = ???
}
