package endpoints.snunit.server

import endpoints.algebra

/**
  * [[algebra.Methods]] interpreter that decodes and encodes methods.
  *
  * @group interpreters
  */
trait Methods extends algebra.Methods {

  type Method = snunit.Method

  def Get = snunit.Method.GET

  def Post = snunit.Method.POST

  def Put = snunit.Method.PUT

  def Delete = snunit.Method.DELETE

  def Options = snunit.Method.OPTIONS

  def Patch = snunit.Method.PATCH
}
