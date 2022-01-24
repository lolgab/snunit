package io.undertow.server

import io.undertow.io.Sender

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

trait BlockingHttpExchange extends Closeable {
  def getInputStream(): InputStream
  def getOutputStream(): OutputStream
  def getSender(): Sender
  def close(): Unit
}
