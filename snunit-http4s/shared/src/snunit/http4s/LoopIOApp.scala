package snunit.http4s

import cats.effect.IOApp
import cats.effect.unsafe.IORuntime

trait LoopIOApp extends IOApp {
  override final lazy val runtime: IORuntime = LoopIORuntime.global
}

object LoopIOApp {
  trait Simple extends IOApp.Simple with LoopIOApp
}
