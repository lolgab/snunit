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

// Release tag of the snunit release holding the prebuilt FreeUnit archives of all platforms.
private val FreeUnitReleaseTag = "freeunit-1.37.0"
private val SNUnitVersion = sys.env.getOrElse("SNUNIT_VERSION", "0.0.0-SNAPSHOT")
private val ReleaseBase = "https://github.com/lolgab/snunit/releases/download"
private val FooterMagic = "SNUNITD1".getBytes("US-ASCII")

private val usage =
  """snunit: build and run SNUnit applications.
    |
    |Usage:
    |  snunit run <scalino args>... [-- <program args>...]   build and run (run is the default command)
    |  snunit package <scalino args>... [-o <output>]        build a single executable embedding unitd
    |  snunit compile <scalino args>...
    |  snunit <anything else>                                forwarded to scalino unchanged
    |
    |The application can be configured with the SNUNIT_PORT (default 8080) and SNUNIT_PROCESSES
    |environment variables.
    |
    |Environment:
    |  SNUNIT_VERSION        snunit library version to depend on
    |  SNUNIT_FREEUNIT_DIR   directory containing `unitd` and `libunit.a`, instead of downloading them
    |
    |Requires scalino: https://github.com/lolgab/scalino""".stripMargin

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
    case "x86_64" | "amd64" => "x86_64"
    case other => die(s"unsupported architecture: $other")
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


// FreeUnit 1.37.0 fails every request on Apple Silicon (libunit now requires the shm segment from the router
// to be exactly PORT_MMAP_SIZE, freeunitorg/freeunit#445, but macOS rounds it up to the 16 KB page size), so
// macOS stays on 1.36.1 until that is fixed upstream. Keep in sync with .github/scripts/build-freeunit.sh.
private def freeUnitVersion: String = if (isMac) "1.36.1" else "1.37.0"

/** Directory with the `unitd` and `libunit.a` for this host, downloaded on first use. */
private def freeUnitDir(): Path =
  sys.env.get("SNUNIT_FREEUNIT_DIR").map(Paths.get(_)).getOrElse {
    val name = s"freeunit-$freeUnitVersion-$platform"
    val dir = cacheDir.resolve(name)
    if (!Files.exists(dir.resolve("unitd"))) {
      val url = s"$ReleaseBase/$FreeUnitReleaseTag/$name.tar.gz"
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

/** Extra scalino flags that turn a Scala program into an SNUnit executable. */
private def buildFlags(dir: Path): Seq[String] = {
  val unitd = dir.resolve("unitd").toAbsolutePath
  val libDir = dir.toAbsolutePath
  val libunit = libDir.resolve("libunit.a")
  Seq("--dep", s"com.github.lolgab::snunit::$SNUnitVersion") ++
    (if (SNUnitVersion.endsWith("SNAPSHOT")) Seq("--repository", "ivy2Local") else Nil) ++
    // snunit reads String internals through raw pointers, which breaks with compact object headers.
    // snunit declares @link("unit"), so -lunit must resolve: -L finds it on machines with no system libunit,
    // and the explicit archive makes the pinned one win over a system-wide install (e.g. /usr/local/lib).
    Seq("--native-compact-headers=false", "--native-linking", s"-L$libDir", "--native-linking", libunit.toString) ++
    // On macOS the unitd bytes become a section of the executable, keeping the code signature valid.
    (if (isMac) Seq("--native-linking", s"-Wl,-sectcreate,__DATA,__unitd,$unitd") else Nil)
}

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
