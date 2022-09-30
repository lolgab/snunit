package snunit.tapir

import snunit.Request
import sttp.model._
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

private[tapir] trait SNUnitInterpreterGeneric {
  private[tapir] type F[_]

  private[tapir] implicit def monadError: MonadError[F]

  private val requestBody: RequestBody[F, NoStreams] = new RequestBody[F, NoStreams] {
    val streams = NoStreams
    def toStream(serverRequest: ServerRequest): streams.BinaryStream = throw new UnsupportedOperationException
    override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW]): F[RawValue[RAW]] = {
      @inline def req = serverRequest.underlying.asInstanceOf[snunit.Request]

      // adapted from tapir Netty implementation
      bodyType match {
        case RawBodyType.StringBody(charset) =>
          monadError.unit(RawValue(new String(req.contentRaw, charset)))
        case RawBodyType.ByteArrayBody  => monadError.unit(RawValue(req.contentRaw))
        case RawBodyType.ByteBufferBody => monadError.unit(RawValue(ByteBuffer.wrap(req.contentRaw)))
        case RawBodyType.InputStreamBody =>
          monadError.unit(RawValue(new ByteArrayInputStream(req.contentRaw)))
        case RawBodyType.FileBody         => ???
        case _: RawBodyType.MultipartBody => ???
      }
    }
  }

  private val toResponseBody: ToResponseBody[Array[Byte], NoStreams] = new ToResponseBody[Array[Byte], NoStreams] {
    val streams = NoStreams
    def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): Array[Byte] = {
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
      body
    }
    def fromStreamValue(
        v: streams.BinaryStream,
        headers: HasHeaders,
        format: CodecFormat,
        charset: Option[Charset]
    ): Array[Byte] = throw new UnsupportedOperationException
    def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
    ): Array[Byte] = throw new UnsupportedOperationException
  }

  private val interceptors: List[Interceptor[F]] = Nil

  private val deleteFile: TapirFile => F[Unit] = _ => monadError.unit(())

  implicit val bodyListener: BodyListener[F, Array[Byte]] = new BodyListener[F, Array[Byte]] {
    def onComplete(body: Array[Byte])(cb: Try[Unit] => F[Unit]): F[Array[Byte]] = ???
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
    def method: Method = req.method match {
      case snunit.Method.GET     => Method.GET
      case snunit.Method.HEAD    => Method.HEAD
      case snunit.Method.POST    => Method.POST
      case snunit.Method.PUT     => Method.PUT
      case snunit.Method.DELETE  => Method.DELETE
      case snunit.Method.OPTIONS => Method.OPTIONS
      case snunit.Method.PATCH   => Method.PATCH
      case snunit.Method.CONNECT => Method.CONNECT
      case snunit.Method.TRACE   => Method.TRACE
      case other                 => Method.unsafeApply(other.name)
    }
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

  def toHandler(endpoints: List[ServerEndpoint[Any, F]]): snunit.Handler = {
    val interpreter = new ServerInterpreter[Any, F, Array[Byte], NoStreams](
      FilterServerEndpoints(endpoints),
      requestBody,
      toResponseBody,
      interceptors,
      deleteFile
    )
    new snunit.Handler {
      def handleRequest(req: Request): Unit = {
        interpreter
          .apply(new SNUnitServerRequest(req))
          .map {
            case RequestResult.Failure(_) =>
              req.send(snunit.StatusCode.NotFound, Array.emptyByteArray, Seq.empty)
            case RequestResult.Response(response) =>
              val body = response.body.getOrElse(Array.emptyByteArray)
              req.send(new snunit.StatusCode(response.code.code), body, response.headers.map(h => h.name -> h.value))
          }
          .handleError { case ex: Exception =>
            System.err.println(s"Error while processing the request")
            ex.printStackTrace()
            req.send(snunit.StatusCode.InternalServerError, Array.emptyByteArray, Seq.empty)
            monadError.unit(())
          }
      }
    }
  }
}
