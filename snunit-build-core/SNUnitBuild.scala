package snunit.build

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file._

// Build-tool independent logic shared by the `snunit` command line tool (Scala Native) and the sbt plugin (JVM):
// downloading FreeUnit, computing the linker options and embedding `unitd` in a finished executable.
// Keep this file free of anything that isn't available on both the JVM and Scala Native, and compatible with both
// Scala 2.12 (sbt 1.x plugin) and Scala 3 (sbt 2.x plugin and the CLI).

/** A failure with a message meant to be shown to the user as is. */
final class SNUnitBuildException(message: String) extends RuntimeException(message)

object SNUnitBuild {
  // FreeUnit commit that `unitd` and `libunit.a` are built from. Keep in sync with .github/freeunit-ref
  // (checked in CI). The prebuilt archives are published in the snunit release tagged `freeunit-<ref8>`.
  val FreeUnitRef = "872bf04170756e919211799ec5574000d628d521"
  val FreeUnitRef8: String = FreeUnitRef.take(8)

  // snunit library version that applications depend on by default. The CLI and the sbt plugin are released
  // independently of the library: bump this when you want new releases to use a newer library (checked against
  // Maven Central by the release workflow).
  val DefaultSNUnitVersion = "0.12.1"

  // Release of the observability stack (Prometheus, Jaeger, VictoriaLogs and Perses built to run inside FreeUnit, see stack/).
  // Keep in sync with .github/stack-release (checked in CI). The archives are published in the snunit release with
  // that tag.
  val StackRelease = "stack-v0.0.1"
  val StackComponents: Seq[String] = Seq("metrics", "traces", "logs", "dashboards")
  val StackPlatforms: Seq[String] = Seq("linux-aarch64", "linux-x86_64", "macos-aarch64", "macos-x86_64")
  private val StackMarker = ".snunit-stack-release"

  private val ReleaseBase = "https://github.com/lolgab/snunit/releases/download"
  private val FooterMagic = "SNUNITD1".getBytes("US-ASCII")

  val platforms: Seq[String] = Seq("linux-aarch64", "linux-x86_64", "macos-aarch64", "macos-x86_64")

  private def fail(message: String): Nothing = throw new SNUnitBuildException(message)

  private def processBuilder(command: Seq[String]): ProcessBuilder = {
    val args = new java.util.ArrayList[String]()
    command.foreach(arg => args.add(arg))
    new ProcessBuilder(args)
  }

  private def spawn(command: Seq[String]): Int =
    try processBuilder(command).inheritIO().start().waitFor()
    catch {
      case e: java.io.IOException => fail(s"cannot run ${command.head}: ${e.getMessage}")
    }

  private def capture(command: Seq[String]): String = {
    val process = processBuilder(command).redirectErrorStream(true).start()
    val output = new String(process.getInputStream.readAllBytes(), "UTF-8").trim
    process.waitFor()
    output
  }

  def isMac: Boolean = System.getProperty("os.name", "").toLowerCase.contains("mac")

  /** `<os>-<arch>` of the machine running the build. */
  def hostPlatform: String = {
    val os = if (isMac) "macos" else "linux"
    val arch = capture(Seq("uname", "-m")) match {
      case "arm64" | "aarch64" => "aarch64"
      case "x86_64" | "amd64"  => "x86_64"
      case other               => fail(s"unsupported architecture: $other")
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

  /**
   * Directory with the `unitd` and `libunit.a` for `target`, downloaded on first use. `SNUNIT_FREEUNIT_DIR` points
   * to a directory to use instead.
   */
  def freeUnitDir(target: String): Path =
    sys.env.get("SNUNIT_FREEUNIT_DIR").map(Paths.get(_)).getOrElse {
      if (!platforms.contains(target))
        fail(s"unknown platform: $target (expected one of ${platforms.mkString(", ")})")
      // Several projects of one build can ask for the same download at the same time.
      SNUnitBuild.synchronized {
        val name = s"freeunit-$FreeUnitRef8-$target"
        val dir = cacheDir.resolve(name)
        if (!Files.exists(dir.resolve("unitd"))) download(name, dir)
        dir
      }
    }

  /** Directory of the observability stack: `SNUNIT_STACK_DIR`, or `stack` in the cache directory. */
  def stackDir: Path = sys.env.get("SNUNIT_STACK_DIR").map(Paths.get(_)).getOrElse(cacheDir.resolve("stack"))

  /** True if all the `components` of the current stack release are installed in `dir`. */
  def stackInstalled(dir: Path, components: Seq[String]): Boolean =
    components.forall { component =>
      val marker = dir.resolve(component).resolve(StackMarker)
      Files.exists(marker) && new String(Files.readAllBytes(marker), "UTF-8").trim == StackRelease
    }

  /**
   * Downloads the `components` of the observability stack for `target` into `dir` (one subdirectory each), unless the
   * current release is already there. The archives are checked against the checksums of the release.
   */
  def installStack(dir: Path, target: String, components: Seq[String]): Unit = {
    if (!StackPlatforms.contains(target))
      fail(s"the observability stack is not available for $target (available: ${StackPlatforms.mkString(", ")})")
    components.foreach { c =>
      if (!StackComponents.contains(c))
        fail(s"unknown stack component: $c (expected ${StackComponents.mkString(", ")})")
    }
    val missing = components.filterNot(c => stackInstalled(dir, Seq(c)))
    if (missing.nonEmpty) {
      Files.createDirectories(dir)
      // SNUNIT_STACK_URL: a mirror of the release (a directory with the archives and SHA256SUMS).
      val base = sys.env.getOrElse("SNUNIT_STACK_URL", s"$ReleaseBase/$StackRelease").stripSuffix("/")
      val sums = Files.createTempFile(dir, "SHA256SUMS", ".tmp")
      try {
        downloadFile(s"$base/SHA256SUMS", sums)
        val checksums = new String(Files.readAllBytes(sums), "UTF-8").split("\n").toList.flatMap { line =>
          line.trim.split("\\s+").toList match {
            case hash :: file :: Nil => Some(file.stripPrefix("*") -> hash)
            case _                   => None
          }
        }.toMap
        missing.foreach { component =>
          val file = s"snunit-stack-$component-$target.tar.gz"
          val tarball = Files.createTempFile(dir, "stack", ".tar.gz")
          val staging = Files.createTempDirectory(dir, "stack-staging")
          try {
            downloadFile(s"$base/$file", tarball)
            val expected = checksums.getOrElse(file, fail(s"no checksum for $file in the release $StackRelease"))
            if (sha256(tarball) != expected) fail(s"checksum mismatch for $file")
            if (spawn(Seq("tar", "xzf", tarball.toString, "-C", staging.toString)) != 0)
              fail(s"cannot extract $file")
            deleteRecursively(dir.resolve(component))
            Files.move(staging.resolve(component), dir.resolve(component), StandardCopyOption.ATOMIC_MOVE)
            Files.write(dir.resolve(component).resolve(StackMarker), StackRelease.getBytes("UTF-8"))
          } finally {
            Files.deleteIfExists(tarball)
            deleteRecursively(staging)
          }
        }
      } finally Files.deleteIfExists(sums)
    }
  }

  private def downloadFile(url: String, to: Path): Unit = {
    System.err.println(s"snunit: downloading $url")
    if (spawn(Seq("curl", "-fL", "--progress-bar", "-o", to.toString, url)) != 0) fail(s"download failed: $url")
  }

  // No MessageDigest on Scala Native: use the tool of the system (sha256sum on Linux, shasum on macOS).
  private def sha256(file: Path): String = {
    val command = if (isMac) Seq("shasum", "-a", "256", file.toString) else Seq("sha256sum", file.toString)
    val output =
      try capture(command)
      catch { case e: java.io.IOException => fail(s"cannot check the checksum: ${e.getMessage}") }
    output.split("\\s+").headOption.getOrElse(fail("cannot read the checksum"))
  }

  private def download(name: String, dir: Path): Unit = {
    val url = s"$ReleaseBase/freeunit-$FreeUnitRef8/$name.tar.gz"
    System.err.println(s"snunit: downloading $url")
    Files.createDirectories(cacheDir)
    val tarball = Files.createTempFile(cacheDir, "freeunit", ".tar.gz")
    // Extract next to the final location and rename, so an interrupted download never leaves a partial directory
    // (and two processes downloading at once don't see each other's half-extracted files).
    val staging = Files.createTempDirectory(cacheDir, "freeunit-staging")
    try {
      if (spawn(Seq("curl", "-fL", "--progress-bar", "-o", tarball.toString, url)) != 0)
        fail(s"download failed: $url")
      if (spawn(Seq("tar", "xzf", tarball.toString, "-C", staging.toString)) != 0)
        fail("cannot extract the FreeUnit archive")
      try Files.move(staging.resolve(name), dir, StandardCopyOption.ATOMIC_MOVE)
      catch {
        // Another process won the race: its copy is as good as ours.
        case _: FileSystemException if Files.exists(dir.resolve("unitd")) => ()
      }
    } finally {
      Files.deleteIfExists(tarball)
      deleteRecursively(staging)
    }
  }

  private def deleteRecursively(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        val paths = List.newBuilder[Path]
        val it = stream.iterator()
        while (it.hasNext) paths += it.next()
        paths.result().reverse.foreach(p => Files.deleteIfExists(p))
      } finally stream.close()
    }

  /** Linker options that link `libunit` and, on macOS, embed `unitd`. Usable from any build tool. */
  def linkOptions(target: String): Seq[String] = {
    val dir = freeUnitDir(target).toAbsolutePath
    val unitd = dir.resolve("unitd")
    val libunit = dir.resolve("libunit.a")
    // snunit declares @link("unit"), so -lunit must resolve: -L finds it on machines with no system libunit,
    // and the explicit archive makes the pinned one win over a system-wide install (e.g. /usr/local/lib).
    Seq(s"-L$dir", libunit.toString) ++
      // On macOS the unitd bytes become a section of the executable, keeping the code signature valid.
      (if (target.startsWith("macos")) Seq(s"-Wl,-sectcreate,__DATA,__unitd,$unitd") else Nil)
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
  def appendPayload(executable: Path, unitd: Path): Unit = {
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

  /**
   * Copies an already linked SNUnit executable to `output` with the `unitd` of `target` appended (Linux only). Can be
   * called again on its own output.
   */
  def bundle(input: Path, output: Path, target: String): Unit = {
    if (!Files.isRegularFile(input)) fail(s"not a file: $input")
    if (target.startsWith("macos"))
      fail(
        "on macOS unitd must be embedded while linking, a finished executable can't be changed without " +
          "invalidating its code signature. Pass the output of `snunit link-flags` to your build tool's linker options"
      )
    val dir = freeUnitDir(target)
    if (input.toAbsolutePath.normalize != output.toAbsolutePath.normalize) {
      Files.createDirectories(output.toAbsolutePath.getParent)
      Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING)
    }
    appendPayload(output, dir.resolve("unitd"))
    System.err.println(s"snunit: bundled unitd ($target) into $output")
  }
}
