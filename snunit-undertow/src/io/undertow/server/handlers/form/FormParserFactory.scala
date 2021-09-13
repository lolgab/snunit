package io.undertow.server.handlers.form

import io.undertow.server.HttpServerExchange

class FormParserFactory private (builder: FormParserFactory.Builder) {
  def createParser(exchange: HttpServerExchange): FormDataParser = ???
}
object FormParserFactory {
  final class Builder private[FormParserFactory] () {
    def build(): FormParserFactory = new FormParserFactory(this)
  }
  def builder(): Builder = new Builder()
}
