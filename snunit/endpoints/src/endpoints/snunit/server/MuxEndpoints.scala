package endpoints
package akkahttp.server

import endpoints.algebra.{Decoder, Encoder, MuxRequest}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Extends the [[Endpoints]] interpreter with [[algebra.MuxEndpoints]]
  * support.
  *
  * @group interpreters
  */
trait MuxEndpoints extends algebra.MuxEndpoints /* with EndpointsWithCustomErrors */ {

  class MuxEndpoint[Req <: MuxRequest, Resp, Transport](
      request: Request[Transport],
      response: Response[Transport]
  ) {

  }

  def muxEndpoint[Req <: MuxRequest, Resp, Transport](
      request: Request[Transport],
      response: Response[Transport]
  ): MuxEndpoint[Req, Resp, Transport] =
    new MuxEndpoint[Req, Resp, Transport](request, response)

}

/**
  * A function whose return type depends on the type
  * of the given `req`.
  *
  * @tparam Req  Request base type
  * @tparam Resp Response base type
  */
trait MuxHandlerAsync[Req <: MuxRequest, Resp] {
  def apply[R <: Resp](req: Req { type Response = R }): Future[R]
}

/**
  * A function whose return type depends on the type
  * of the given `req`.
  *
  * @tparam Req  Request base type
  * @tparam Resp Response base type
  */
trait MuxHandler[Req <: MuxRequest, Resp] {
  def apply[R <: Resp](req: Req { type Response = R }): R
}
