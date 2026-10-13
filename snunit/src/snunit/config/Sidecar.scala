package snunit.config

import scala.concurrent.duration.*

/** A process run and supervised by the launcher next to FreeUnit, for example an OpenTelemetry collector.
  *
  * FreeUnit only runs applications speaking its own protocol, so tools like these are started by the launcher: it
  * starts them with FreeUnit, restarts them when they exit (with a growing delay), and stops them after FreeUnit has
  * drained the running requests, so that they receive the last telemetry. They share the standard output and error of
  * the launcher. To reach one from outside, route a path of a listener to it with [[Action.Proxy]].
  *
  * @param name
  *   identifies the sidecar in the metrics
  * @param command
  *   executable, looked up in the `PATH` unless it contains a `/`
  * @param stopTimeout
  *   time the process has to exit after SIGTERM, before being killed
  */
final case class Sidecar(
    name: String,
    command: String,
    arguments: Seq[String] = Nil,
    /** Added to the environment of the launcher. */
    environment: Map[String, String] = Map.empty,
    workingDirectory: Option[String] = None,
    restart: Sidecar.Restart = Sidecar.Restart.Always,
    stopTimeout: FiniteDuration = 10.seconds
)

object Sidecar {
  enum Restart {

    /** Restart whenever the process exits. */
    case Always

    /** Restart when the process exits with an error. */
    case OnFailure

    /** Never restart. */
    case Never
  }

  /** An [[https://opentelemetry.io/docs/collector/ OpenTelemetry collector]] reading `config`. Use `otelcol` for the
    * core distribution.
    */
  def otelCollector(config: String, command: String = "otelcol-contrib"): Sidecar =
    Sidecar("otel-collector", command, Seq("--config", config))
}
