package snunit.tests

import snunit.tapir.SNUnitIdServerInterpreter.*
import snunit.tapir.SNUnitStreams
import sttp.tapir.*

object TapirWebsocketSync {
  val echo = endpoint.get
    .in("echo")
    .out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](SNUnitStreams))
    .serverLogicSuccess[Id](_ => (message: String) => List(message))

  def main(args: Array[String]): Unit =
    snunit.SyncServerBuilder
      .setRequestHandler(toHandler(echo :: Nil))
      .setWebsocketHandler(websocketHandler)
      .build()
      .listen()
}
