package snunit.cli

import java.nio.file.*
import _root_.snunit.build.*
import scala.jdk.CollectionConverters.*
import scala.scalanative.posix.signal
import scala.scalanative.unsafe.*

// The `snunit` command line tool: a thin wrapper around `scalino` that builds
// SNUnit applications into a single executable embedding `unitd`.
//
// Build it with: scalino package snunit-cli/snunit.scala -o snunit

// The FreeUnit commit, the default snunit library version and everything about downloading and embedding
// `unitd` live in snunit-build-core, shared with the sbt plugin. Build with both:
//
//   scalino package snunit-build-core snunit-cli/snunit.scala -o snunit

private val usage =
  """snunit: build and run SNUnit applications.
    |
    |Usage:
    |  snunit run <scalino args>... [-- <program args>...]   build and run (run is the default command)
    |  snunit package <scalino args>... [-o <output>]        build a single executable embedding unitd
    |  snunit compile <scalino args>...
    |  snunit bundle <binary> [-o <output>] [--platform <os-arch>]
    |                                                        embed unitd in an SNUnit executable built by
    |                                                        any build tool (Linux only, see below)
    |  snunit link-flags [--platform <os-arch>]              print the linker options (one per line) that
    |                                                        link libunit and, on macOS, embed unitd
    |  snunit stack install [--dir <dir>] [--platform <os-arch>] [metrics|traces|logs|dashboards]...
    |                                                        download the observability stack (metrics, traces,
    |                                                        logs, dashboards) that runs inside FreeUnit
    |  snunit stack path                                     the directory the stack is installed in
    |  snunit run --stack ...                                run with the stack (installed first if needed) and open
    |                                                        dashboards on http://localhost:3000, with no change in the code
    |  snunit <anything else>                                forwarded to scalino unchanged
    |
    |The application listens on port 8080 by default. Everything else (port, processes, TLS, timeouts...)
    |is configured in the code with snunit.config.UnitConfig.
    |
    |Environment:
    |  SNUNIT_FREEUNIT_DIR   directory containing `unitd` and `libunit.a`, instead of downloading them
    |  SNUNIT_STACK_DIR      directory of the observability stack (default: ~/.cache/snunit/stack)
    |  SNUNIT_STACK_URL      mirror of the stack release to download from
    |
    |<os-arch> is linux-x86_64, linux-aarch64, macos-x86_64 or macos-aarch64 (default: this machine).
    |
    |`run`, `package` and `compile` need scalino: https://github.com/lolgab/scalino
    |`bundle` and `link-flags` don't. On macOS unitd can only be embedded while linking, so pass the
    |`link-flags` output to your build tool's linker options instead of using `bundle`.""".stripMargin

private def die(message: String): Nothing = {
  System.err.println(s"snunit: $message")
  sys.exit(1)
}

private def spawn(command: Seq[String]): Int =
  try new ProcessBuilder(command.asJava).inheritIO().start().waitFor()
  catch {
    case e: java.io.IOException => die(s"cannot run ${command.head}: ${e.getMessage}")
  }

private def isMac: Boolean = SNUnitBuild.isMac

private def platform: String = guard(SNUnitBuild.hostPlatform)

/** Runs `body`, turning build failures into a message and exit code. */
private def guard[A](body: => A): A =
  try body
  catch { case e: SNUnitBuildException => die(e.getMessage) }

private def scalinoCheck(): Unit =
  if (spawn(Seq("scalino", "version")) != 0)
    die("scalino not found. Install it from https://github.com/lolgab/scalino")

/** Extra scalino flags that turn a Scala program into an SNUnit executable. */
private def buildFlags(): Seq[String] =
  guard(SNUnitBuild.linkOptions(platform)).flatMap(Seq("--native-linking", _))

/** Splits `args` at the first `--`. */
private def splitProgramArgs(args: Seq[String]): (Seq[String], Seq[String]) = {
  val index = args.indexOf("--")
  if (index < 0) (args, Nil) else (args.take(index), args.drop(index + 1))
}

/** Takes `-o <file>` / `--output <file>` out of `args`. */
private def extractOutput(args: Seq[String]): (Seq[String], Option[String]) = {
  val index = args.indexWhere(a => a == "-o" || a == "--output")
  if (index < 0 || index == args.length - 1) (args, None)
  else (args.take(index) ++ args.drop(index + 2), Some(args(index + 1)))
}

/** Takes `<name> <value>` out of `args`. */
private def extractOption(args: Seq[String], name: String): (Seq[String], Option[String]) = {
  val index = args.indexOf(name)
  if (index < 0 || index == args.length - 1) (args, None)
  else (args.take(index) ++ args.drop(index + 2), Some(args(index + 1)))
}

/** Builds the executable at `output`. Returns the scalino exit code. */
private def build(command: String, args: Seq[String], output: Path): Int = {
  scalinoCheck()
  val code = spawn(Seq("scalino", command) ++ args ++ buildFlags() ++ Seq("-o", output.toString))
  if (code == 0 && !isMac) guard(SNUnitBuild.bundle(output, output, platform))
  code
}

@main def snunit(args: String*): Unit = args.toList match {
  case Nil | ("-h" | "--help" | "help") :: _ => println(usage)
  case "package" :: rest =>
    val (scalinoArgs, output) = extractOutput(rest)
    sys.exit(build("package", scalinoArgs, Paths.get(output.getOrElse("app")).toAbsolutePath))
  case "compile" :: rest =>
    scalinoCheck()
    sys.exit(spawn(Seq("scalino", "compile") ++ rest ++ buildFlags()))
  case "bundle" :: rest =>
    val (withoutOutput, output) = extractOutput(rest)
    val (positional, target) = extractOption(withoutOutput, "--platform")
    positional match {
      case Seq(input) =>
        guard(
          SNUnitBuild.bundle(
            Paths.get(input).toAbsolutePath,
            Paths.get(output.getOrElse(input)).toAbsolutePath,
            target.getOrElse(platform)
          )
        )
      case _ => die("usage: snunit bundle <binary> [-o <output>] [--platform <os-arch>]")
    }
  case "link-flags" :: rest =>
    val (_, target) = extractOption(rest, "--platform")
    guard(SNUnitBuild.linkOptions(target.getOrElse(platform))).foreach(println)
  case "stack" :: rest => stack(rest)
  case ("run" :: rest) => run(rest)
  case first :: _ if first.startsWith("-") || first.endsWith(".scala") || Files.exists(Paths.get(first)) =>
    run(args.toList)
  case other => // Not a build command: forward to scalino unchanged.
    sys.exit(spawn("scalino" +: other))
}

private def stack(args: List[String]): Unit = args match {
  case "install" :: rest =>
    val (withoutDir, dir) = extractOption(rest, "--dir")
    val (components, target) = extractOption(withoutDir, "--platform")
    val directory = dir.map(Paths.get(_).toAbsolutePath).getOrElse(SNUnitBuild.stackDir)
    guard(
      SNUnitBuild.installStack(
        directory,
        target.getOrElse(platform),
        if (components.isEmpty) SNUnitBuild.StackComponents else components
      )
    )
    println(s"snunit: the observability stack is in $directory")
    if (dir.nonEmpty) println(s"snunit: run the application with SNUNIT_STACK_DIR=$directory")
  case "path" :: Nil => println(SNUnitBuild.stackDir)
  case _ => die("usage: snunit stack install [--dir <dir>] [--platform <os-arch>] [component]... | snunit stack path")
}

@volatile private var childPid = 0
@volatile private var terminating = false

private val forwardSignal = CFuncPtr1.fromScalaFunction[CInt, Unit] { sig =>
  terminating = true
  val pid = childPid
  if (pid > 0) signal.kill(pid, sig)
}

/** Starts the built application. SIGTERM sent to snunit is forwarded to it. */
private def start(output: Path, programArgs: Seq[String]): Process = {
  val builder = new ProcessBuilder((output.toString +: programArgs).asJava).inheritIO()
  applicationEnvironment.foreach((k, v) => builder.environment().put(k, v))
  val process = builder.start()
  childPid = process.pid().toInt
  signal.signal(signal.SIGTERM, forwardSignal)
  process
}

private val watchFlags = Set("-w", "--watch", "--watching")

/** Environment of the application: where the observability stack is when it was asked for with `--stack`. */
@volatile private var applicationEnvironment = Map.empty[String, String]

/** Latest modification time of all the `.scala` files and `scalino` directives reachable from `paths`. */
private def lastModified(paths: Seq[String]): Long =
  paths.iterator
    .map(Paths.get(_))
    .filter(Files.exists(_))
    .flatMap { path =>
      if (Files.isDirectory(path)) Files.walk(path).iterator().asScala.filter(_.toString.endsWith(".scala"))
      else Iterator.single(path)
    }
    .map(Files.getLastModifiedTime(_).toMillis)
    .foldLeft(0L)(math.max)

private def run(args: Seq[String]): Unit = {
  val (allScalinoArgs, programArgs) = splitProgramArgs(args)
  val watch = allScalinoArgs.exists(watchFlags)
  val withStack = allScalinoArgs.contains("--stack")
  val scalinoArgs = allScalinoArgs.filterNot(a => watchFlags(a) || a == "--stack")
  if (withStack) {
    guard(SNUnitBuild.installStack(SNUnitBuild.stackDir, platform, SNUnitBuild.StackComponents))
    applicationEnvironment = Map("SNUNIT_STACK" -> "1", "SNUNIT_STACK_DIR" -> SNUnitBuild.stackDir.toString)
  }
  val output = Paths.get(".snunit-build", "app").toAbsolutePath
  Files.createDirectories(output.getParent)

  if (!watch) {
    val code = build("package", scalinoArgs, output)
    if (code != 0) sys.exit(code)
    sys.exit(start(output, programArgs).waitFor())
  } else {
    // Sources are the arguments that exist on disk.
    val sources = scalinoArgs.filter(a => !a.startsWith("-") && Files.exists(Paths.get(a)))
    var seen = -1L
    var running: Option[Process] = None
    while (!terminating) {
      val modified = lastModified(sources)
      if (modified != seen) {
        seen = modified
        running.foreach { process =>
          process.destroy()
          process.waitFor()
        }
        running = None
        if (build("package", scalinoArgs, output) == 0) running = Some(start(output, programArgs))
        else System.err.println("snunit: build failed, waiting for changes")
      }
      Thread.sleep(500)
    }
    running.foreach(_.waitFor())
  }
}
