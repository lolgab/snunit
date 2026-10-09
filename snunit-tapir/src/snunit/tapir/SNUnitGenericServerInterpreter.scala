package snunit.tapir

import snunit.*
import sttp.model._
import sttp.model.{Method as TapirMethod}
import sttp.monad._
import sttp.monad.syntax._
import sttp.tapir._
import sttp.tapir.capabilities._
import sttp.tapir.model._
import sttp.tapir.server._
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter._

import java.io._
import java.nio._
import java.nio.charset._
import java.nio.file._
import scala.collection.immutable.ArraySeq
import scala.util._

private[tapir] trait SNUnitGenericServerInterpreter {
  private[tapir] type Wrapper[_]
  private[tapir] type HandlerWrapper
  private[tapir] type Caps
  private[tapir] type S <: sttp.capabilities.Streams[S]
  private[tapir] val streamsInstance: S

  private[tapir] sealed trait Body
  private[tapir] final class BytesBody(val bytes: Array[Byte]) extends Body

  /** A websocket response. `prepare` is invoked before the connection is upgraded, so that the frames sent by the
    * client right after the upgrade are not lost. The returned action is run once the connection is upgraded.
    */
  private[tapir] final class WebSocketBody(val prepare: snunit.Request => Wrapper[Wrapper[Unit]]) extends Body

  /** Converts the pipe of a websocket endpoint (of type `streams.Pipe[REQ, RESP]`) into a body */
  private[tapir] def webSocketBody[REQ, RESP](
      pipe: Any,
      o: WebSocketBodyOutput[?, REQ, RESP, ?, S]
  ): WebSocketBody = throw new UnsupportedOperationException("WebSockets are not supported by this interpreter")

  private[tapir] implicit def monadError: MonadError[Wrapper]
  private[tapir] trait WrapperDispatcher {
    @inline def dispatch(f: => Wrapper[Unit]): Unit
  }
  private[tapir] val dispatcher: WrapperDispatcher
  @inline private[tapir] def createHandleWrapper(f: => snunit.RequestHandler): HandlerWrapper
  @inline private[tapir] def wrapSideEffect[T](f: => T): Wrapper[T]

  private val requestBody: RequestBody[Wrapper, S] = new RequestBody[Wrapper, S] {
    val streams: S = streamsInstance
    def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
      throw new UnsupportedOperationException
    override def toRaw[RAW](
        serverRequest: ServerRequest,
        bodyType: RawBodyType[RAW],
        maxBytes: Option[Long]
    ): Wrapper[RawValue[RAW]] = {
      inline def req = serverRequest.underlying.asInstanceOf[snunit.Request]

      // adapted from tapir Netty implementation
      bodyType match {
        case RawBodyType.StringBody(charset) =>
          monadError.unit(RawValue(new String(req.contentRaw(), charset)))
        case RawBodyType.ByteArrayBody  => monadError.unit(RawValue(req.contentRaw()))
        case RawBodyType.ByteBufferBody => monadError.unit(RawValue(ByteBuffer.wrap(req.contentRaw())))
        case RawBodyType.InputStreamBody =>
          monadError.unit(RawValue(new ByteArrayInputStream(req.contentRaw())))
        case RawBodyType.FileBody         => ???
        case _: RawBodyType.MultipartBody => ???
      }
    }
  }

  private val toResponseBody: ToResponseBody[Body, S] = new ToResponseBody[Body, S] {
    val streams: S = streamsInstance
    def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): Body = {
      val body: Array[Byte] = bodyType match {
        case RawBodyType.StringBody(charset) =>
          v.toString.getBytes(charset)
        case RawBodyType.ByteArrayBody =>
          val bytes = v.asInstanceOf[Array[Byte]]
          bytes
        case RawBodyType.ByteBufferBody =>
          val byteBuffer = v.asInstanceOf[ByteBuffer]
          byteBuffer.array()

        // case RawBodyType.InputStreamBody =>
        //   val stream = v.asInstanceOf[InputStream]
        //   stream.readAllBytes()
        //   ???

        case RawBodyType.FileBody         => Files.readAllBytes(v.file.toPath)
        case _: RawBodyType.MultipartBody => ???
      }
      new BytesBody(body)
    }
    def fromStreamValue(
        v: streams.BinaryStream,
        headers: HasHeaders,
        format: CodecFormat,
        charset: Option[Charset]
    ): Body = throw new UnsupportedOperationException
    def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, S]
    ): Body = webSocketBody[REQ, RESP](pipe, o)
  }

  private val interceptors: List[Interceptor[Wrapper]] = Nil

  private val deleteFile: TapirFile => Wrapper[Unit] = _ => monadError.unit(())

  implicit val bodyListener: BodyListener[Wrapper, Body] = new BodyListener[Wrapper, Body] {
    def onComplete(body: Body)(cb: Try[Unit] => Wrapper[Unit]): Wrapper[Body] =
      cb(Success(())).map(_ => body)
  }

  private class SNUnitServerRequest(req: snunit.Request) extends ServerRequest {
    // Members declared in sttp.model.HasHeaders
    def headers: Seq[Header] = {
      val array = new Array[Header](req.headersLength)
      var i = 0
      while (i < req.headersLength) {
        array(i) = Header(req.headerNameUnsafe(i), req.headerValueUnsafe(i))
        i += 1
      }
      ArraySeq.unsafeWrapArray(array)
    }

    // Members declared in sttp.model.RequestMetadata
    def method: TapirMethod = TapirMethod.unsafeApply(req.method)
    def uri: sttp.model.Uri = Uri.unsafeParse(req.target)

    // Members declared in sttp.tapir.model.ServerRequest
    def attribute[T](k: sttp.tapir.AttributeKey[T], v: T): sttp.tapir.model.ServerRequest = ???
    def attribute[T](k: sttp.tapir.AttributeKey[T]): Option[T] = ???
    def connectionInfo: sttp.tapir.model.ConnectionInfo = ???
    def pathSegments: List[String] = uri.pathSegments.segments.map(_.v).toList
    def protocol: String = ???
    def queryParameters: sttp.model.QueryParams = uri.params
    def underlying: Any = req
    def withUnderlying(underlying: Any): sttp.tapir.model.ServerRequest = ???
  }

  def toHandler(endpoints: List[ServerEndpoint[Caps, Wrapper]]): HandlerWrapper = {
    val interpreter = new ServerInterpreter[Caps, Wrapper, Body, S](
      FilterServerEndpoints(endpoints),
      requestBody,
      toResponseBody,
      interceptors,
      deleteFile
    )
    createHandleWrapper {
      new snunit.RequestHandler {
        def handleRequest(req: Request): Unit = {
          dispatcher.dispatch {
            interpreter
              .apply(new SNUnitServerRequest(req))
              .flatMap {
                case RequestResult.Failure(_) =>
                  wrapSideEffect(
                    req.send(snunit.StatusCode.NotFound, Array.emptyByteArray, snunit.Headers.empty)
                  )
                case RequestResult.Response(response, _) =>
                  response.body match {
                    case Some(ws: WebSocketBody) =>
                      if (req.isWebsocketHandshake)
                        ws.prepare(req).flatMap(start => wrapSideEffect(req.upgrade()).flatMap(_ => start))
                      else
                        wrapSideEffect(
                          req.send(snunit.StatusCode.BadRequest, Array.emptyByteArray, snunit.Headers.empty)
                        )
                    case body =>
                      val bytes = body match {
                        case Some(b: BytesBody) => b.bytes
                        case _                  => Array.emptyByteArray
                      }
                      val headers = snunit.Headers(response.headers, _.name, _.value)
                      wrapSideEffect(
                        req.send(snunit.StatusCode(response.code.code), bytes, headers)
                      )
                  }
              }
              .handleError { case ex: Exception =>
                wrapSideEffect {
                  System.err.println(s"Error while processing the request")
                  ex.printStackTrace()
                  req.send(snunit.StatusCode.InternalServerError, Array.emptyByteArray, snunit.Headers.empty)
                }
              }
          }
        }
      }
    }
  }
}
