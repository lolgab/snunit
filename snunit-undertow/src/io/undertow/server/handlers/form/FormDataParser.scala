package io.undertow.server.handlers.form

trait FormDataParser {
  def parseBlocking(): FormData
}