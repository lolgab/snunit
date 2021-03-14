package cask.util

abstract class WebsocketClientImpl(url: String) extends WebsocketBase {
  def connect(): Unit = ???
  def send(value: String) = ???
  def send(value: Array[Byte]) = ???
  def close(): Unit = ???
  def isClosed() = ???
}
