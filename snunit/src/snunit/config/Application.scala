package snunit.config

import scala.concurrent.duration.FiniteDuration

/** How many application processes FreeUnit runs. */
enum Processes {

  /** A fixed number of processes. */
  case Fixed(count: Int)

  /** Between `spare` and `max` processes, started on demand. Idle processes above `spare` are stopped after
    * `idleTimeout`.
    */
  case Dynamic(max: Int, spare: Int = 0, idleTimeout: Option[FiniteDuration] = None)

  private[config] def toJson: Json = this match {
    case Fixed(count) => Json.int(count)
    case Dynamic(max, spare, idleTimeout) =>
      Json.obj { o =>
        o.put("max", Json.int(max))
        o.put("spare", Json.int(spare))
        o.put("idle_timeout", idleTimeout)(Json.seconds)
      }
  }
}

/** Limits of the application processes. */
final case class Limits(
    /** Maximum number of requests a process serves before being restarted. */
    requests: Option[Int] = None,
    /** Maximum time a request can take. */
    timeout: Option[FiniteDuration] = None,
    /** Maximum time a process has to start. */
    startTimeout: Option[FiniteDuration] = None,
    /** Size in bytes of the shared memory used to exchange data with the router. */
    sharedMemory: Option[Long] = None
) {
  private[config] def toJson: Json = Json.obj { o =>
    o.put("requests", requests)(Json.int(_))
    o.put("timeout", timeout)(Json.seconds)
    o.put("start_timeout", startTimeout)(Json.seconds)
    o.put("shm", sharedMemory)(Json.int(_))
  }
}

/** Maps a range of IDs inside the application namespace to a range of IDs of the host. */
final case class IdMap(container: Int, host: Int, size: Int) {
  private[config] def toJson: Json = Json.obj { o =>
    o.put("container", Json.int(container))
    o.put("host", Json.int(host))
    o.put("size", Json.int(size))
  }
}

/** The namespaces created for the application, unset ones keep the FreeUnit default. */
final case class Namespaces(
    cgroup: Option[Boolean] = None,
    credential: Option[Boolean] = None,
    mount: Option[Boolean] = None,
    network: Option[Boolean] = None,
    pid: Option[Boolean] = None,
    uname: Option[Boolean] = None
) {
  private[config] def toJson: Json = Json.obj { o =>
    o.put("cgroup", cgroup)(Json.Bool(_))
    o.put("credential", credential)(Json.Bool(_))
    o.put("mount", mount)(Json.Bool(_))
    o.put("network", network)(Json.Bool(_))
    o.put("pid", pid)(Json.Bool(_))
    o.put("uname", uname)(Json.Bool(_))
  }
}

/** What is mounted automatically when `rootfs` is set. */
final case class Automount(
    languageDeps: Option[Boolean] = None,
    procfs: Option[Boolean] = None,
    tmpfs: Option[Boolean] = None
) {
  private[config] def toJson: Json = Json.obj { o =>
    o.put("language_deps", languageDeps)(Json.Bool(_))
    o.put("procfs", procfs)(Json.Bool(_))
    o.put("tmpfs", tmpfs)(Json.Bool(_))
  }
}

/** Isolation of the application processes. */
final case class Isolation(
    namespaces: Option[Namespaces] = None,
    uidMap: Seq[IdMap] = Nil,
    gidMap: Seq[IdMap] = Nil,
    /** Directory used as the root of the file system of the application. */
    rootfs: Option[String] = None,
    automount: Option[Automount] = None,
    newPrivileges: Option[Boolean] = None,
    /** Path of the cgroup (v2) the application runs in. */
    cgroupPath: Option[String] = None
) {
  private[config] def toJson: Json = Json.obj { o =>
    o.put("namespaces", namespaces)(_.toJson)
    if (uidMap.nonEmpty) o.put("uidmap", Json.Arr(uidMap.map(_.toJson)))
    if (gidMap.nonEmpty) o.put("gidmap", Json.Arr(gidMap.map(_.toJson)))
    o.put("rootfs", rootfs)(Json.Str(_))
    o.put("automount", automount)(_.toJson)
    o.put("new_privs", newPrivileges)(Json.Bool(_))
    o.put("cgroup", cgroupPath)(path => Json.obj(_.put("path", Json.Str(path))))
  }
}

/** The SNUnit application: the executable itself, run by FreeUnit as an `external` application. */
final case class Application(
    /** Command line arguments of the application processes. */
    arguments: Seq[String] = Nil,
    environment: Map[String, String] = Map.empty,
    /** User and group the application processes run as. Default: the user and group of the launcher. */
    user: Option[String] = None,
    group: Option[String] = None,
    workingDirectory: Option[String] = None,
    /** Files where the standard output and error of the application are redirected. */
    stdout: Option[String] = None,
    stderr: Option[String] = None,
    processes: Option[Processes] = None,
    limits: Option[Limits] = None,
    isolation: Option[Isolation] = None
) {
  def withProcesses(count: Int): Application = copy(processes = Some(Processes.Fixed(count)))

  private[config] def toJson(executable: String): Json = Json.obj { o =>
    o.put("type", Json.Str("external"))
    o.put("executable", Json.Str(executable))
    if (arguments.nonEmpty) o.put("arguments", Json.strings(arguments))
    o.putMap("environment", environment)
    o.put("user", user)(Json.Str(_))
    o.put("group", group)(Json.Str(_))
    o.put("working_directory", workingDirectory)(Json.Str(_))
    o.put("stdout", stdout)(Json.Str(_))
    o.put("stderr", stderr)(Json.Str(_))
    o.put("processes", processes)(_.toJson)
    o.put("limits", limits)(_.toJson)
    o.put("isolation", isolation)(_.toJson)
  }
}
