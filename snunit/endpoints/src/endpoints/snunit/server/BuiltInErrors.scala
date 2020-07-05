package endpoints.snunit.server

import endpoints.{Invalid, algebra}

/**
  * @group interpreters
  */
trait BuiltInErrors extends algebra.BuiltInErrors {
  this: EndpointsWithCustomErrors =>

  def clientErrorsResponseEntity: ResponseEntity[Invalid] =
    ???

  def serverErrorResponseEntity: ResponseEntity[Throwable] =
    ???

}
