package endpoints.snunit.server

import endpoints.algebra

/**
  * [[algebra.StatusCodes]] interpreter that decodes and encodes methods.
  *
  * @group interpreters
  */
trait StatusCodes extends algebra.StatusCodes {

  type StatusCode = snunit.StatusCode

  def OK = snunit.StatusCode.OK
  def Created = snunit.StatusCode.Created
  def Accepted = snunit.StatusCode.Accepted
  def NoContent = snunit.StatusCode.NoContent
  def BadRequest = snunit.StatusCode.BadRequest
  def Unauthorized = snunit.StatusCode.Unauthorized
  def Forbidden = snunit.StatusCode.Forbidden
  def NotFound = snunit.StatusCode.NotFound
  def PayloadTooLarge = snunit.StatusCode.PayloadTooLarge
  def TooManyRequests = snunit.StatusCode.TooManyRequests
  def InternalServerError = snunit.StatusCode.InternalServerError
  def NotImplemented = snunit.StatusCode.NotImplemented

}
