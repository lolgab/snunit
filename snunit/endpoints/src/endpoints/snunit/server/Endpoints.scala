package endpoints.snunit.server

import endpoints.algebra.Documentation
import endpoints.{
  Invalid,
  InvariantFunctor,
  PartialInvariantFunctor,
  Semigroupal,
  Tupler,
  Valid,
  Validated,
  algebra
}

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/**
  * Interpreter for [[algebra.Endpoints]] that performs routing using Akka-HTTP and uses [[algebra.BuiltInErrors]]
  * to model client and server errors.
  *
  * @group interpreters
  */
trait Endpoints
    extends algebra.Endpoints
    with EndpointsWithCustomErrors
    with BuiltInErrors

/**
  * Interpreter for [[algebra.Endpoints]] that performs routing using Akka-HTTP.
  * @group interpreters
  */
trait EndpointsWithCustomErrors
    extends algebra.EndpointsWithCustomErrors
    with Urls
    with Methods
    with StatusCodes {

}
