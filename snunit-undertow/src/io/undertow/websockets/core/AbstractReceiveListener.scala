package io.undertow.websockets.core

import org.xnio.ChannelListener

trait AbstractReceiveListener extends ChannelListener[WebSocketChannel] {
  protected def onFullTextMessage(channel: WebSocketChannel, message: BufferedTextMessage): Unit = {}
  protected def onFullBinaryMessage(channel: WebSocketChannel, message: BufferedBinaryMessage): Unit = {}
  protected def onFullPingMessage(channel: WebSocketChannel, message: BufferedBinaryMessage): Unit = {}
  protected def onFullPongMessage(channel: WebSocketChannel, message: BufferedBinaryMessage): Unit = {}
  protected def onCloseMessage(cm: CloseMessage, channel: WebSocketChannel): Unit = {}
}
