package org.java_websocket.client

import java.net.URI

class WebSocketClient(uri: URI) {
  def connect(): Unit = ???
  def send(message: String): Unit = ???
  def send(message: Array[Byte]): Unit = ???
  def close(): Unit = ???
  def isClosed(): Boolean = ???
}
