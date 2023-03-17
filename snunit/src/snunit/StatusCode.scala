package snunit

import scala.annotation.targetName

type StatusCode = Short

object StatusCode {
  def apply(value: Short): StatusCode = value
  def apply(value: Int): StatusCode = value.toShort

  final val Continue: StatusCode = 100
  final val SwitchingProtocols: StatusCode = 101
  final val Processing: StatusCode = 102
  final val OK: StatusCode = 200
  final val Created: StatusCode = 201
  final val Accepted: StatusCode = 202
  final val NonAuthoritativeInformation: StatusCode = 203
  final val NoContent: StatusCode = 204
  final val ResetContent: StatusCode = 205
  final val PartialContent: StatusCode = 206
  final val MultiStatus: StatusCode = 207
  final val AlreadyReported: StatusCode = 208
  final val IMUsed: StatusCode = 226
  final val MultipleChoices: StatusCode = 300
  final val MovedPermanently: StatusCode = 301
  final val Found: StatusCode = 302
  final val SeeOther: StatusCode = 303
  final val NotModified: StatusCode = 304
  final val UseProxy: StatusCode = 305
  final val TemporaryRedirect: StatusCode = 307
  final val PermanentRedirect: StatusCode = 308
  final val BadRequest: StatusCode = 400
  final val Unauthorized: StatusCode = 401
  final val PaymentRequired: StatusCode = 402
  final val Forbidden: StatusCode = 403
  final val NotFound: StatusCode = 404
  final val MethodNotAllowed: StatusCode = 405
  final val NotAcceptable: StatusCode = 406
  final val ProxyAuthenticationRequired: StatusCode = 407
  final val RequestTimeout: StatusCode = 408
  final val Conflict: StatusCode = 409
  final val Gone: StatusCode = 410
  final val LengthRequired: StatusCode = 411
  final val PreconditionFailed: StatusCode = 412
  final val PayloadTooLarge: StatusCode = 413
  final val UriTooLong: StatusCode = 414
  final val UnsupportedMediaType: StatusCode = 415
  final val RangeNotSatisfiable: StatusCode = 416
  final val ExpectationFailed: StatusCode = 417
  final val ImATeapot: StatusCode = 418
  final val EnhanceYourCalm: StatusCode = 420
  final val MisdirectedRequest: StatusCode = 421
  final val UnprocessableEntity: StatusCode = 422
  final val Locked: StatusCode = 423
  final val FailedDependency: StatusCode = 424
  final val TooEarly: StatusCode = 425
  final val UpgradeRequired: StatusCode = 426
  final val PreconditionRequired: StatusCode = 428
  final val TooManyRequests: StatusCode = 429
  final val RequestHeaderFieldsTooLarge: StatusCode = 431
  final val RetryWith: StatusCode = 449
  final val BlockedByParentalControls: StatusCode = 450
  final val UnavailableForLegalReasons: StatusCode = 451
  final val InternalServerError: StatusCode = 500
  final val NotImplemented: StatusCode = 501
  final val BadGateway: StatusCode = 502
  final val ServiceUnavailable: StatusCode = 503
  final val GatewayTimeout: StatusCode = 504
  final val HttpVersionNotSupported: StatusCode = 505
  final val VariantAlsoNegotiates: StatusCode = 506
  final val InsufficientStorage: StatusCode = 507
  final val LoopDetected: StatusCode = 508
  final val BandwidthLimitExceeded: StatusCode = 509
  final val NotExtended: StatusCode = 510
  final val NetworkAuthenticationRequired: StatusCode = 511
  final val NetworkReadTimeout: StatusCode = 598
  final val NetworkConnectTimeout: StatusCode = 599
}
