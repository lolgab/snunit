package snunit.cli

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.*
import scala.jdk.CollectionConverters.*
import scala.scalanative.posix.signal
import scala.scalanative.unsafe.*

// The `snunit` command line tool: a thin wrapper around `scalino` that builds
// SNUnit applications into a single executable embedding `unitd`.
//
// Build it with: scalino package snunit-cli/snunit.scala -o snunit

// FreeUnit commit that `unitd` and `libunit.a` are built from. Keep in sync with .github/freeunit-ref
// (checked in CI). The prebuilt archives are published in the snunit release tagged `freeunit-<ref8>`.
private val FreeUnitRef = "872bf04170756e919211799ec5574000d628d521"
private val FreeUnitRef8 = FreeUnitRef.take(8)
// snunit library version that applications depend on by default. The CLI is released independently of the
// library: bump this when you want new CLI releases to use a newer library (checked against Maven Central
// by the release workflow). Override with SNUNIT_VERSION.
private val DefaultSNUnitVersion = "0.11.0"
private val SNUnitVersion = sys.env.getOrElse("SNUNIT_VERSION", DefaultSNUnitVersion)
private val ReleaseBase = "https://github.com/lolgab/snunit/releases/download"
private val FooterMagic = "SNUNITD1".getBytes("US-ASCII")

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
    |  snunit <anything else>                                forwarded to scalino unchanged
    |
    |The application can be configured with the SNUNIT_PORT (default 8080) and SNUNIT_PROCESSES
    |environment variables.
    |
    |Environment:
    |  SNUNIT_VERSION        snunit library version to depend on
    |  SNUNIT_FREEUNIT_DIR   directory containing `unitd` and `libunit.a`, instead of downloading them
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

private def capture(command: Seq[String]): String = {
  val process = new ProcessBuilder(command.asJava).redirectErrorStream(true).start()
  val output = new String(process.getInputStream.readAllBytes(), "UTF-8").trim
  process.waitFor()
  output
}

private def isMac: Boolean = System.getProperty("os.name", "").toLowerCase.contains("mac")

private def platform: String = {
  val os = if (isMac) "macos" else "linux"
  val arch = capture(Seq("uname", "-m")) match {
    case "arm64" | "aarch64" => "aarch64"
    case "x86_64" | "amd64"  => "x86_64"
    case other               => die(s"unsupported architecture: $other")
  }
  s"$os-$arch"
}

private def cacheDir: Path = {
  val base = sys.env
    .get("XDG_CACHE_HOME")
    .map(Paths.get(_))
    .getOrElse(Paths.get(System.getProperty("user.home"), ".cache"))
  base.resolve("snunit")
}

private val platforms = Set("linux-x86_64", "linux-aarch64", "macos-x86_64", "macos-aarch64")

/** Directory with the `unitd` and `libunit.a` for `target`, downloaded on first use. */
private def freeUnitDir(target: String = platform): Path =
  sys.env.get("SNUNIT_FREEUNIT_DIR").map(Paths.get(_)).getOrElse {
    if (!platforms(target)) die(s"unknown platform: $target (expected one of ${platforms.toSeq.sorted.mkString(", ")})")
    val name = s"freeunit-$FreeUnitRef8-$target"
    val dir = cacheDir.resolve(name)
    if (!Files.exists(dir.resolve("unitd"))) {
      val url = s"$ReleaseBase/freeunit-$FreeUnitRef8/$name.tar.gz"
      System.err.println(s"snunit: downloading $url")
      Files.createDirectories(cacheDir)
      val tarball = Files.createTempFile(cacheDir, "freeunit", ".tar.gz")
      try {
        if (spawn(Seq("curl", "-fL", "--progress-bar", "-o", tarball.toString, url)) != 0)
          die(s"download failed: $url")
        if (spawn(Seq("tar", "xzf", tarball.toString, "-C", cacheDir.toString)) != 0)
          die("cannot extract the FreeUnit archive")
      } finally Files.deleteIfExists(tarball)
    }
    dir
  }

private def scalinoCheck(): Unit =
  if (spawn(Seq("scalino", "version")) != 0)
    die("scalino not found. Install it from https://github.com/lolgab/scalino")

/** Linker options that link `libunit` and, on macOS, embed `unitd`. Usable from any build tool. */
private def linkOptions(dir: Path, macos: Boolean): Seq[String] = {
  val unitd = dir.resolve("unitd").toAbsolutePath
  val libDir = dir.toAbsolutePath
  val libunit = libDir.resolve("libunit.a")
  // snunit declares @link("unit"), so -lunit must resolve: -L finds it on machines with no system libunit,
  // and the explicit archive makes the pinned one win over a system-wide install (e.g. /usr/local/lib).
  Seq(s"-L$libDir", libunit.toString) ++
    // On macOS the unitd bytes become a section of the executable, keeping the code signature valid.
    (if (macos) Seq(s"-Wl,-sectcreate,__DATA,__unitd,$unitd") else Nil)
}

/** Extra scalino flags that turn a Scala program into an SNUnit executable. */
private def buildFlags(dir: Path): Seq[String] =
  Seq("--dep", s"com.github.lolgab::snunit::$SNUnitVersion") ++
    (if (sys.env.contains("SNUNIT_VERSION")) Seq("--repository", "ivy2Local") else Nil) ++
    // snunit reads String internals through raw pointers, which breaks with compact object headers.
    Seq("--native-compact-headers=false") ++
    linkOptions(dir, isMac).flatMap(Seq("--native-linking", _))

/** Removes a previously appended payload, if any, so appending is idempotent. */
private def stripPayload(file: RandomAccessFile): Unit = {
  val length = file.length()
  if (length >= 16) {
    val footer = new Array[Byte](16)
    file.seek(length - 16)
    file.readFully(footer)
    if (java.util.Arrays.equals(footer.slice(8, 16), FooterMagic)) {
      val size = ByteBuffer.wrap(footer, 0, 8).order(ByteOrder.LITTLE_ENDIAN).getLong
      file.setLength(length - 16 - size)
    }
  }
}

/** Linux: append the unitd bytes and a footer (little-endian size + magic) to the executable. */
private def appendPayload(executable: Path, unitd: Path): Unit = {
  val payload = Files.readAllBytes(unitd)
  val file = new RandomAccessFile(executable.toFile, "rw")
  try {
    stripPayload(file)
    file.seek(file.length())
    file.write(payload)
    file.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(payload.length.toLong).array())
    file.write(FooterMagic)
  } finally file.close()
}

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

/** Copies an already linked SNUnit executable to `output` with the `unitd` of `target` appended (Linux only). */
private def bundle(input: Path, output: Path, target: String): Unit = {
  if (!Files.isRegularFile(input)) die(s"not a file: $input")
  if (target.startsWith("macos"))
    die(
      "on macOS unitd must be embedded while linking, a finished executable can't be changed without " +
        "invalidating its code signature. Pass the output of `snunit link-flags` to your build tool's linker options"
    )
  val dir = freeUnitDir(target)
  if (input.normalize != output.normalize)
    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING)
  appendPayload(output, dir.resolve("unitd"))
  System.err.println(s"snunit: bundled unitd ($target) into $output")
}

/** Builds the executable at `output`. Returns the scalino exit code. */
private def build(command: String, args: Seq[String], output: Path): Int = {
  scalinoCheck()
  val dir = freeUnitDir()
  val code = spawn(Seq("scalino", command) ++ args ++ buildFlags(dir) ++ Seq("-o", output.toString))
  if (code == 0 && !isMac) appendPayload(output, dir.resolve("unitd"))
  code
}

@main def snunit(args: String*): Unit = args.toList match {
  case Nil | ("-h" | "--help" | "help") :: _ => println(usage)
  case "package" :: rest =>
    val (scalinoArgs, output) = extractOutput(rest)
    sys.exit(build("package", scalinoArgs, Paths.get(output.getOrElse("app")).toAbsolutePath))
  case "compile" :: rest =>
    scalinoCheck()
    sys.exit(spawn(Seq("scalino", "compile") ++ rest ++ buildFlags(freeUnitDir())))
  case "bundle" :: rest =>
    val (withoutOutput, output) = extractOutput(rest)
    val (positional, target) = extractOption(withoutOutput, "--platform")
    positional match {
      case Seq(input) =>
        bundle(
          Paths.get(input).toAbsolutePath,
          Paths.get(output.getOrElse(input)).toAbsolutePath,
          target.getOrElse(platform)
        )
      case _ => die("usage: snunit bundle <binary> [-o <output>] [--platform <os-arch>]")
    }
  case "link-flags" :: rest =>
    val (_, target) = extractOption(rest, "--platform")
    val chosen = target.getOrElse(platform)
    linkOptions(freeUnitDir(chosen), chosen.startsWith("macos")).foreach(println)
  case ("run" :: rest) => run(rest)
  case first :: _ if first.startsWith("-") || first.endsWith(".scala") || Files.exists(Paths.get(first)) =>
    run(args.toList)
  case other => // Not a build command: forward to scalino unchanged.
    sys.exit(spawn("scalino" +: other))
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
  val process = new ProcessBuilder((output.toString +: programArgs).asJava).inheritIO().start()
  childPid = process.pid().toInt
  signal.signal(signal.SIGTERM, forwardSignal)
  process
}

private val watchFlags = Set("-w", "--watch", "--watching")

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
  val scalinoArgs = allScalinoArgs.filterNot(watchFlags)
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
