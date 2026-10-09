package snunit.http4s

/** The websocket builder of the http4s version in use: `WebSocketBuilder2` in http4s 0.23, `WebSocketBuilder` in 1.x */
type SNUnitWebSocketBuilder[F[_]] = org.http4s.server.websocket.WebSocketBuilder2[F]
