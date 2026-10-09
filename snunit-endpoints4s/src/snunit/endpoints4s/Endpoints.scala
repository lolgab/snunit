package snunit.endpoints4s

import _root_.endpoints4s.algebra
import _root_.endpoints4s.algebra.Documentation
import _root_.endpoints4s.{Invalid, InvariantFunctor, PartialInvariantFunctor, Semigroupal, Tupler, Valid, Validated}

import snunit.{contentRaw, headerNameUnsafe, headerValueUnsafe, headersLength, method, path, query, send}

import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import scala.collection.Factory
import scala.collection.mutable
import scala.util.control.NonFatal

/** A fully built HTTP response, ready to be sent with SNUnit. */
final case class HttpResponse(status: snunit.StatusCode, headers: List[(String, String)], body: Array[Byte])

/** Interpreter for [[algebra.Endpoints]] based on SNUnit. It uses [[algebra.BuiltInErrors]] to model client and server
  * errors.
  *
  * {{{
  *   trait MyEndpoints extends algebra.Endpoints with algebra.JsonEntitiesFromSchemas {
  *     val inc = endpoint(get(path / "inc" /? qs[Int]("x")), ok(jsonResponse[Int]))
  *   }
  *
  *   object MyServer extends snunit.endpoints4s.Endpoints with MyEndpoints {
  *     def main(args: Array[String]): Unit =
  *       snunit.SyncServerBuilder
  *         .setRequestHandler(toHandler(inc.implementedBy(_ + 1)))
  *         .build()
  *         .listen()
  *   }
  * }}}
  */
class Endpoints extends algebra.Endpoints with EndpointsWithCustomErrors with BuiltInErrors

trait EndpointsWithCustomErrors extends algebra.EndpointsWithCustomErrors with Methods with Urls {

  type Route = snunit.Request => Option[HttpResponse]

  trait Request[A] {

    /** Information extracted from the URL and the headers */
    type UrlAndHeaders

    /** Checks whether the incoming request matches this request description, parses its URL parameters and headers, and
      * then parses its entity if there was no previous validation errors.
      */
    final def matches(req: snunit.Request): Option[Either[HttpResponse, A]] =
      matchAndParseHeaders(req).map {
        case Left(response)                  => Left(response)
        case Right(invalid: Invalid)         => Left(handleClientErrors(req, invalid))
        case Right(Valid(urlAndHeadersData)) => parseEntity(urlAndHeadersData, req)
      }

    /** @return
      *   `None` if the incoming request does not match this request method and URL. Otherwise `Some(Left(response))` to
      *   immediately return a custom response, `Some(Right(Valid(_)))` if the URL and headers were successfully parsed,
      *   or `Some(Right(Invalid(_)))` in case of validation errors.
      */
    def matchAndParseHeaders(req: snunit.Request): Option[Either[HttpResponse, Validated[UrlAndHeaders]]]

    def parseEntity(urlAndHeaders: UrlAndHeaders, req: snunit.Request): Either[HttpResponse, A]
  }

  type RequestHeaders[A] = snunit.Request => Validated[A]

  type RequestEntity[A] = snunit.Request => Either[HttpResponse, A]

  /** Body of a response and its content type, if any. */
  final case class Entity(contentType: Option[String], body: Array[Byte])

  type ResponseEntity[A] = A => Entity

  type ResponseHeaders[A] = A => List[(String, String)]

  type Response[A] = A => HttpResponse

  case class Endpoint[A, B](request: Request[A], response: Response[B], operationId: Option[String]) {

    def implementedBy(implementation: A => B): Route = { req =>
      try {
        request.matches(req).map {
          case Right(a) =>
            try response(implementation(a))
            catch { case NonFatal(t) => handleServerError(req, t) }
          case Left(errorResponse) => errorResponse
        }
      } catch {
        case NonFatal(t) => Some(handleServerError(req, t))
      }
    }
  }

  /** Builds a request handler that tries every route in order. It replies with `404 Not Found` if no route matches. */
  def toHandler(routes: Route*): snunit.RequestHandler = {
    val all = routes.toArray
    new snunit.RequestHandler {
      def handleRequest(req: snunit.Request): Unit = {
        var response: Option[HttpResponse] = None
        var i = 0
        while (response.isEmpty && i < all.length) {
          response = all(i)(req)
          i += 1
        }
        val r = response.getOrElse(HttpResponse(snunit.StatusCode.NotFound, Nil, Array.emptyByteArray))
        req.send(r.status, r.body, snunit.Headers(r.headers, _._1, _._2))
      }
    }
  }

  // HEADERS
  def emptyRequestHeaders: RequestHeaders[Unit] = _ => Valid(())

  def requestHeader(name: String, docs: Documentation): RequestHeaders[String] =
    req =>
      findHeader(req, name) match {
        case Some(value) => Valid(value)
        case None        => Invalid(s"Missing header $name")
      }

  def optRequestHeader(name: String, docs: Documentation): RequestHeaders[Option[String]] =
    req => Valid(findHeader(req, name))

  private[endpoints4s] def findHeader(req: snunit.Request, name: String): Option[String] = {
    var i = 0
    while (i < req.headersLength) {
      if (req.headerNameUnsafe(i).equalsIgnoreCase(name)) return Some(req.headerValueUnsafe(i))
      i += 1
    }
    None
  }

  implicit def requestHeadersPartialInvariantFunctor: PartialInvariantFunctor[RequestHeaders] =
    new PartialInvariantFunctor[RequestHeaders] {
      def xmapPartial[From, To](f: RequestHeaders[From], map: From => Validated[To], contramap: To => From) =
        req => f(req).flatMap(map)
    }

  implicit def requestHeadersSemigroupal: Semigroupal[RequestHeaders] =
    new Semigroupal[RequestHeaders] {
      def product[A, B](fa: RequestHeaders[A], fb: RequestHeaders[B])(implicit tupler: Tupler[A, B]) =
        req => fa(req).zip(fb(req))
    }

  // RESPONSES
  implicit lazy val responseInvariantFunctor: InvariantFunctor[Response] =
    new InvariantFunctor[Response] {
      def xmap[A, B](fa: Response[A], f: A => B, g: B => A): Response[B] = fa compose g
    }

  implicit def responseEntityInvariantFunctor: InvariantFunctor[ResponseEntity] =
    new InvariantFunctor[ResponseEntity] {
      def xmap[A, B](fa: ResponseEntity[A], f: A => B, g: B => A): ResponseEntity[B] = fa compose g
    }

  implicit def responseHeadersSemigroupal: Semigroupal[ResponseHeaders] =
    new Semigroupal[ResponseHeaders] {
      def product[A, B](fa: ResponseHeaders[A], fb: ResponseHeaders[B])(implicit tupler: Tupler[A, B]) =
        out => {
          val (a, b) = tupler.unapply(out)
          fa(a) ++ fb(b)
        }
    }

  implicit def responseHeadersInvariantFunctor: InvariantFunctor[ResponseHeaders] =
    new InvariantFunctor[ResponseHeaders] {
      def xmap[A, B](fa: ResponseHeaders[A], f: A => B, g: B => A): ResponseHeaders[B] = fa compose g
    }

  override def addResponseHeaders[A, H](response: Response[A], headers: ResponseHeaders[H])(implicit
      tupler: Tupler[A, H]
  ): Response[tupler.Out] =
    out => {
      val (a, h) = tupler.unapply(out)
      val r = response(a)
      r.copy(headers = r.headers ++ headers(h))
    }

  def response[A, B, R](
      statusCode: StatusCode,
      entity: ResponseEntity[A],
      docs: Documentation,
      headers: ResponseHeaders[B]
  )(implicit tupler: Tupler.Aux[A, B, R]): Response[R] =
    r => {
      val (a, b) = tupler.unapply(r)
      val e = entity(a)
      HttpResponse(statusCode, e.contentType.map("Content-Type" -> _).toList ++ headers(b), e.body)
    }

  def choiceResponse[A, B](responseA: Response[A], responseB: Response[B]): Response[Either[A, B]] = {
    case Left(a)  => responseA(a)
    case Right(b) => responseB(b)
  }

  def emptyResponse: ResponseEntity[Unit] = _ => Entity(None, Array.emptyByteArray)

  def textResponse: ResponseEntity[String] = s => Entity(Some("text/plain; charset=utf-8"), s.getBytes(UTF_8))

  def emptyResponseHeaders: ResponseHeaders[Unit] = _ => Nil

  def responseHeader(name: String, docs: Documentation = None): ResponseHeaders[String] =
    value => (name -> value) :: Nil

  def optResponseHeader(name: String, docs: Documentation = None): ResponseHeaders[Option[String]] =
    _.map(name -> _).toList

  // ENDPOINTS
  def endpoint[A, B](request: Request[A], response: Response[B], docs: EndpointDocs = EndpointDocs()): Endpoint[A, B] =
    Endpoint(request, response, docs.operationId)

  override def mapEndpointRequest[A, B, C](
      currentEndpoint: Endpoint[A, B],
      func: Request[A] => Request[C]
  ): Endpoint[C, B] = endpoint(func(currentEndpoint.request), currentEndpoint.response)

  override def mapEndpointResponse[A, B, C](
      currentEndpoint: Endpoint[A, B],
      func: Response[B] => Response[C]
  ): Endpoint[A, C] = endpoint(currentEndpoint.request, func(currentEndpoint.response))

  override def mapEndpointDocs[A, B](
      currentEndpoint: Endpoint[A, B],
      func: EndpointDocs => EndpointDocs
  ): Endpoint[A, B] = currentEndpoint

  // REQUESTS
  implicit def requestPartialInvariantFunctor: PartialInvariantFunctor[Request] =
    new PartialInvariantFunctor[Request] {
      def xmapPartial[A, B](fa: Request[A], f: A => Validated[B], g: B => A): Request[B] =
        new Request[B] {
          type UrlAndHeaders = fa.UrlAndHeaders

          def matchAndParseHeaders(req: snunit.Request) = fa.matchAndParseHeaders(req)

          def parseEntity(urlAndHeaders: UrlAndHeaders, req: snunit.Request): Either[HttpResponse, B] =
            fa.parseEntity(urlAndHeaders, req).flatMap { a =>
              f(a) match {
                case Valid(b)         => Right(b)
                case invalid: Invalid => Left(handleClientErrors(req, invalid))
              }
            }
        }
    }

  implicit def requestEntityPartialInvariantFunctor: PartialInvariantFunctor[RequestEntity] =
    new PartialInvariantFunctor[RequestEntity] {
      def xmapPartial[From, To](f: RequestEntity[From], map: From => Validated[To], contramap: To => From) =
        req =>
          f(req).flatMap { from =>
            map(from) match {
              case Valid(to)        => Right(to)
              case invalid: Invalid => Left(handleClientErrors(req, invalid))
            }
          }
    }

  def emptyRequest: RequestEntity[Unit] = _ => Right(())

  def textRequest: RequestEntity[String] = req => Right(new String(req.contentRaw(), UTF_8))

  def choiceRequestEntity[A, B](
      requestEntityA: RequestEntity[A],
      requestEntityB: RequestEntity[B]
  ): RequestEntity[Either[A, B]] =
    req =>
      requestEntityA(req) match {
        case Right(a) => Right(Left(a))
        case Left(_)  => requestEntityB(req).map(Right(_))
      }

  def request[UrlP, BodyP, HeadersP, UrlAndBodyPTupled, Out](
      method: Method,
      url: Url[UrlP],
      entity: RequestEntity[BodyP] = emptyRequest,
      docs: Documentation = None,
      headers: RequestHeaders[HeadersP] = emptyRequestHeaders
  )(implicit
      tuplerUB: Tupler.Aux[UrlP, BodyP, UrlAndBodyPTupled],
      tuplerUBH: Tupler.Aux[UrlAndBodyPTupled, HeadersP, Out]
  ): Request[Out] =
    new Request[Out] {
      type UrlAndHeaders = (UrlP, HeadersP)

      def matchAndParseHeaders(req: snunit.Request) =
        matchAndParseHeadersAsRight(method, url, headers, req)

      def parseEntity(urlAndHeaders: UrlAndHeaders, req: snunit.Request): Either[HttpResponse, Out] =
        entity(req).map(body => tuplerUBH(tuplerUB(urlAndHeaders._1, body), urlAndHeaders._2))
    }

  override def addRequestHeaders[A, H](request: Request[A], headersP: RequestHeaders[H])(implicit
      tupler: Tupler[A, H]
  ): Request[tupler.Out] =
    new Request[tupler.Out] {
      type UrlAndHeaders = (request.UrlAndHeaders, H)

      def matchAndParseHeaders(req: snunit.Request) =
        request.matchAndParseHeaders(req).map(_.map(_.zip(headersP(req))))

      def parseEntity(urlAndHeaders: UrlAndHeaders, req: snunit.Request): Either[HttpResponse, tupler.Out] =
        request.parseEntity(urlAndHeaders._1, req).map(a => tupler(a, urlAndHeaders._2))
    }

  override def addRequestQueryString[A, Q](request: Request[A], qs: QueryString[Q])(implicit
      tupler: Tupler[A, Q]
  ): Request[tupler.Out] =
    new Request[tupler.Out] {
      type UrlAndHeaders = (request.UrlAndHeaders, Q)

      def matchAndParseHeaders(req: snunit.Request) =
        request.matchAndParseHeaders(req).map(_.map(_.zip(qs(queryParams(req)))))

      def parseEntity(urlAndHeaders: UrlAndHeaders, req: snunit.Request): Either[HttpResponse, tupler.Out] =
        request.parseEntity(urlAndHeaders._1, req).map(a => tupler(a, urlAndHeaders._2))
    }

  /** Default implementation for `matchAndParseHeaders` which never returns a `Left(response)`. It checks that the
    * incoming request matches the given `method` and `url`. If this is the case, it parses the request headers.
    */
  protected final def matchAndParseHeadersAsRight[U, H](
      method: Method,
      url: Url[U],
      headers: RequestHeaders[H],
      req: snunit.Request
  ): Option[Either[HttpResponse, Validated[(U, H)]]] =
    if (req.method == method) url.decodeUrl(req).map(validatedUrl => Right(validatedUrl.zip(headers(req))))
    else None

  /** Called when decoding a request failed. Can be overridden to customize the error reporting. */
  def handleClientErrors(req: snunit.Request, invalid: Invalid): HttpResponse =
    clientErrorsResponse(invalidToClientErrors(invalid))

  /** Called when an exception is thrown during request processing. Can be overridden to customize the error reporting.
    */
  def handleServerError(req: snunit.Request, throwable: Throwable): HttpResponse =
    serverErrorResponse(throwableToServerError(throwable))
}

trait Methods extends algebra.Methods {
  type Method = snunit.Method

  def Get: Method = snunit.Method.GET
  def Post: Method = snunit.Method.POST
  def Put: Method = snunit.Method.PUT
  def Delete: Method = snunit.Method.DELETE
  def Patch: Method = snunit.Method.PATCH
  def Options: Method = snunit.Method.OPTIONS
}

trait StatusCodes extends algebra.StatusCodes {
  type StatusCode = snunit.StatusCode

  def OK = snunit.StatusCode.OK
  def Created = snunit.StatusCode.Created
  def Accepted = snunit.StatusCode.Accepted
  override def NonAuthoritativeInformation = snunit.StatusCode.NonAuthoritativeInformation
  def NoContent = snunit.StatusCode.NoContent
  override def ResetContent = snunit.StatusCode.ResetContent
  override def PartialContent = snunit.StatusCode.PartialContent
  override def MultiStatus = snunit.StatusCode.MultiStatus
  override def AlreadyReported = snunit.StatusCode.AlreadyReported
  override def IMUsed = snunit.StatusCode.IMUsed

  override def NotModified = snunit.StatusCode.NotModified
  override def TemporaryRedirect = snunit.StatusCode.TemporaryRedirect
  override def PermanentRedirect = snunit.StatusCode.PermanentRedirect

  def BadRequest = snunit.StatusCode.BadRequest
  def Unauthorized = snunit.StatusCode.Unauthorized
  override def PaymentRequired = snunit.StatusCode.PaymentRequired
  def Forbidden = snunit.StatusCode.Forbidden
  def NotFound = snunit.StatusCode.NotFound
  override def MethodNotAllowed = snunit.StatusCode.MethodNotAllowed
  override def NotAcceptable = snunit.StatusCode.NotAcceptable
  override def ProxyAuthenticationRequired = snunit.StatusCode.ProxyAuthenticationRequired
  override def RequestTimeout = snunit.StatusCode.RequestTimeout
  override def Conflict = snunit.StatusCode.Conflict
  override def Gone = snunit.StatusCode.Gone
  override def LengthRequired = snunit.StatusCode.LengthRequired
  override def PreconditionFailed = snunit.StatusCode.PreconditionFailed
  def PayloadTooLarge = snunit.StatusCode.PayloadTooLarge
  override def UriTooLong = snunit.StatusCode.UriTooLong
  override def UnsupportedMediaType = snunit.StatusCode.UnsupportedMediaType
  override def RangeNotSatisfiable = snunit.StatusCode.RangeNotSatisfiable
  override def ExpectationFailed = snunit.StatusCode.ExpectationFailed
  override def MisdirectedRequest = snunit.StatusCode.MisdirectedRequest
  override def UnprocessableEntity = snunit.StatusCode.UnprocessableEntity
  override def Locked = snunit.StatusCode.Locked
  override def FailedDependency = snunit.StatusCode.FailedDependency
  override def TooEarly = snunit.StatusCode.TooEarly
  override def UpgradeRequired = snunit.StatusCode.UpgradeRequired
  override def PreconditionRequired = snunit.StatusCode.PreconditionRequired
  def TooManyRequests = snunit.StatusCode.TooManyRequests
  override def RequestHeaderFieldsTooLarge = snunit.StatusCode.RequestHeaderFieldsTooLarge
  override def UnavailableForLegalReasons = snunit.StatusCode.UnavailableForLegalReasons

  def InternalServerError = snunit.StatusCode.InternalServerError
  def NotImplemented = snunit.StatusCode.NotImplemented
}

trait BuiltInErrors extends algebra.BuiltInErrors { this: EndpointsWithCustomErrors =>

  def clientErrorsResponseEntity: ResponseEntity[Invalid] =
    invalid => Entity(Some("application/json"), invalidToJson(invalid).getBytes(UTF_8))

  def serverErrorResponseEntity: ResponseEntity[Throwable] =
    th => clientErrorsResponseEntity(Invalid(th.getMessage))

  private def invalidToJson(invalid: Invalid): String = {
    val arr = ujson.Arr()
    invalid.errors.foreach(e => arr.value += ujson.Str(e))
    ujson.write(arr)
  }
}

trait Urls extends algebra.Urls with StatusCodes {
  this: EndpointsWithCustomErrors =>

  type Params = Map[String, Seq[String]]

  type QueryString[A] = Params => Validated[A]

  trait QueryStringParam[A] {
    def decode(name: String, params: Params): Validated[A]
  }

  trait Url[A] {
    def decodeUrl(req: snunit.Request): Option[Validated[A]]
  }

  trait Path[A] extends Url[A] {
    def decode(paths: List[String]): Option[(Validated[A], List[String])]

    final def decodeUrl(req: snunit.Request): Option[Validated[A]] = pathExtractor(this, req)
  }

  trait Segment[A] {
    def decode(rawSegment: String): Validated[A]
  }

  private[endpoints4s] def queryParams(req: snunit.Request): Params = {
    val query = req.query
    if (query.isEmpty) Map.empty
    else {
      val result = mutable.LinkedHashMap.empty[String, Vector[String]]
      query.split('&').foreach { pair =>
        if (pair.nonEmpty) {
          val idx = pair.indexOf('=')
          val (k, v) = if (idx < 0) (pair, "") else (pair.substring(0, idx), pair.substring(idx + 1))
          val key = URLDecoder.decode(k, UTF_8.name())
          result.update(key, result.getOrElse(key, Vector.empty) :+ URLDecoder.decode(v, UTF_8.name()))
        }
      }
      result.toMap
    }
  }

  def combineQueryStrings[A, B](first: QueryString[A], second: QueryString[B])(implicit
      tupler: Tupler[A, B]
  ): QueryString[tupler.Out] =
    map => first(map).zip(second(map))

  def qs[A](name: String, docs: Documentation = None)(implicit value: QueryStringParam[A]): QueryString[A] =
    params => value.decode(name, params).mapErrors(_.map(error => s"$error for query parameter '$name'"))

  type WithDefault[A] = A

  override def optQsWithDefault[A](name: String, default: A, docs: Documentation = None)(implicit
      value: QueryStringParam[A]
  ): QueryString[WithDefault[A]] =
    qs(name, docs)(optionalQueryStringParam(value)).xmap(_.getOrElse(default))(Some(_))

  implicit def optionalQueryStringParam[A](implicit param: QueryStringParam[A]): QueryStringParam[Option[A]] =
    (name, params) =>
      params.get(name) match {
        case None    => Valid(None)
        case Some(_) => param.decode(name, params).map(Some(_))
      }

  implicit def repeatedQueryStringParam[A, CC[X] <: Iterable[X]](implicit
      param: QueryStringParam[A],
      factory: Factory[A, CC[A]]
  ): QueryStringParam[CC[A]] =
    (name: String, qs: Params) =>
      qs.get(name) match {
        case None => Valid(factory.newBuilder.result())
        case Some(vs) =>
          vs.foldLeft[Validated[mutable.Builder[A, CC[A]]]](Valid(factory.newBuilder)) {
            case (inv: Invalid, v) =>
              param
                .decode(name, Map(name -> (v :: Nil)))
                .fold(_ => inv, errors => Invalid(inv.errors ++ errors))
            case (Valid(b), v) =>
              param.decode(name, Map(name -> (v :: Nil))).map(b += _)
          }.map(_.result())
      }

  implicit def queryStringParamPartialInvariantFunctor: PartialInvariantFunctor[QueryStringParam] =
    new PartialInvariantFunctor[QueryStringParam] {
      def xmapPartial[A, B](fa: QueryStringParam[A], f: A => Validated[B], g: B => A): QueryStringParam[B] =
        (str, params) => fa.decode(str, params).flatMap(f)
    }

  implicit def stringQueryString: QueryStringParam[String] =
    (name, params) => Validated.fromOption(params.get(name).flatMap(_.headOption))("Missing value")

  implicit def segmentPartialInvariantFunctor: PartialInvariantFunctor[Segment] =
    new PartialInvariantFunctor[Segment] {
      def xmapPartial[A, B](fa: Segment[A], f: A => Validated[B], g: B => A): Segment[B] =
        s => fa.decode(s).flatMap(f)
    }

  implicit def pathPartialInvariantFunctor: PartialInvariantFunctor[Path] =
    new PartialInvariantFunctor[Path] {
      def xmapPartial[A, B](fa: Path[A], f: A => Validated[B], g: B => A): Path[B] =
        new Path[B] {
          def decode(paths: List[String]): Option[(Validated[B], List[String])] =
            fa.decode(paths).map { case (validA, rs) => (validA.flatMap(f), rs) }
        }
    }

  def staticPathSegment(segment: String): Path[Unit] =
    new Path[Unit] {
      def decode(paths: List[String]): Option[(Validated[Unit], List[String])] =
        paths match {
          case s :: ss if s == segment => Some((Valid(()), ss))
          case _                       => None
        }
    }

  def segment[A](name: String = "", docs: Documentation = None)(implicit A: Segment[A]): Path[A] =
    new Path[A] {
      def decode(segments: List[String]): Option[(Validated[A], List[String])] =
        segments match {
          case head :: tail =>
            val validatedA = A
              .decode(head)
              .mapErrors(_.map(error => s"$error for segment${if (name.isEmpty) "" else s" '$name'"}"))
            Some((validatedA, tail))
          case Nil => None
        }
    }

  implicit def stringSegment: Segment[String] = Valid(_)

  def remainingSegments(name: String = "", docs: Documentation = None): Path[String] =
    new Path[String] {
      def decode(segments: List[String]): Option[(Validated[String], List[String])] =
        if (segments.isEmpty) None
        else
          Some(
            (Valid(segments.map(java.net.URLEncoder.encode(_, UTF_8.name())).mkString("/")), Nil)
          )
    }

  def chainPaths[A, B](first: Path[A], second: Path[B])(implicit tupler: Tupler[A, B]): Path[tupler.Out] =
    new Path[tupler.Out] {
      def decode(segments: List[String]): Option[(Validated[tupler.Out], List[String])] =
        first.decode(segments).flatMap { case (validA, segments2) =>
          second.decode(segments2).map { case (validB, segments3) =>
            (validA.zip(validB)(tupler), segments3)
          }
        }
    }

  implicit def urlPartialInvariantFunctor: PartialInvariantFunctor[Url] =
    new PartialInvariantFunctor[Url] {
      def xmapPartial[A, B](fa: Url[A], f: A => Validated[B], g: B => A): Url[B] =
        new Url[B] {
          def decodeUrl(req: snunit.Request): Option[Validated[B]] = fa.decodeUrl(req).map(_.flatMap(f))
        }
    }

  def urlWithQueryString[A, B](path: Path[A], qs: QueryString[B])(implicit tupler: Tupler[A, B]): Url[tupler.Out] =
    new Url[tupler.Out] {
      def decodeUrl(req: snunit.Request): Option[Validated[tupler.Out]] =
        pathExtractor(path, req).map(_.zip(qs(queryParams(req)))(tupler))
    }

  private def pathExtractor[A](path: Path[A], req: snunit.Request): Option[Validated[A]] = {
    val rawPath = req.path
    val segments =
      rawPath.split("/").map(URLDecoder.decode(_, UTF_8.name())).toList ++ {
        if (rawPath.endsWith("/")) List("") else Nil
      }

    path.decode(if (segments.isEmpty) List("") else segments).flatMap {
      case (validated, Nil) => Some(validated)
      case (_, _)           => None
    }
  }

  implicit def queryStringPartialInvariantFunctor: PartialInvariantFunctor[QueryString] =
    new PartialInvariantFunctor[QueryString] {
      def xmapPartial[A, B](fa: Params => Validated[A], f: A => Validated[B], g: B => A): Params => Validated[B] =
        params => fa(params).flatMap(f)
    }
}
