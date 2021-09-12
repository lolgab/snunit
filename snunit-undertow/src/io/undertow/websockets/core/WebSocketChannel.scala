package io.undertow.websockets.core

import org.xnio.ChannelListener

abstract class WebSocketChannel extends java.nio.channels.Channel {
  def suspendReceives(): Unit
  def addCloseTask(handleEvent: WebSocketChannel => Unit) = ???
  def getReceiveSetter(): ChannelListener.Setter[WebSocketChannel] = ???
  def resumeReceives(): Unit = ???
}
