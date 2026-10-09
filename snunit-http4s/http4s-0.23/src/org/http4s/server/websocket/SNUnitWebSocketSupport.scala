package org.http4s.server.websocket

import cats.Applicative
import cats.effect.kernel.Concurrent
import cats.effect.kernel.Unique
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream
import org.http4s.Response
import org.http4s.websocket.WebSocketCombinedPipe
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketSeparatePipe

/** Gives access to the `private[http4s]` parts of the http4s websocket API */
object SNUnitWebSocketSupport {
  def newBuilder[F[_]: Applicative: Unique]: F[WebSocketBuilder2[F]] = WebSocketBuilder2[F]

  /** If the response was created by `webSocketBuilder`, returns the pipe to run on the connection and the `onClose`
    * action
    */
  def extract[F[_]: Concurrent](
      webSocketBuilder: WebSocketBuilder2[F],
      response: Response[F]
  ): Option[(Pipe[F, WebSocketFrame, WebSocketFrame], F[Unit])] =
    response.attributes.lookup(webSocketBuilder.webSocketKey).map { context =>
      context.webSocket match {
        case WebSocketCombinedPipe(receiveSend, onClose) => (receiveSend, onClose)
        case WebSocketSeparatePipe(send, receive, onClose) =>
          val pipe: Pipe[F, WebSocketFrame, WebSocketFrame] = in => send.concurrently(in.through(receive))
          (pipe, onClose)
      }
    }
}
