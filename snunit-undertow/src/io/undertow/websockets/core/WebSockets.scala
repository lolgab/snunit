package io.undertow.websockets.core

import java.nio.ByteBuffer

object WebSockets {
  def mergeBuffers(payload: ByteBuffer*): ByteBuffer = ???
  def sendTextBlocking(value: String, channel: WebSocketChannel): Unit = ???
  def sendBinaryBlocking(value: ByteBuffer, channel: WebSocketChannel): Unit = ???
  def sendPingBlocking(value: ByteBuffer, channel: WebSocketChannel): Unit = ???
  def sendPongBlocking(value: ByteBuffer, channel: WebSocketChannel): Unit = ???
  def sendCloseBlocking(code: Int, reason: String, channel: WebSocketChannel): Unit = ???
}
