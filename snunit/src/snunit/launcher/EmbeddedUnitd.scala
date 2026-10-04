package snunit.launcher

import java.io.RandomAccessFile
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.nio.file.attribute.PosixFilePermissions
import scala.scalanative.libc.string.memcpy
import scala.scalanative.meta.LinktimeInfo
import scala.scalanative.posix.unistd
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

/** Access to the `unitd` binary embedded in the running executable.
  *
  * How it gets there is decided at link time by the `snunit` CLI:
  *   - macOS: `-Wl,-sectcreate,__DATA,__unitd,<unitd>` (keeps the code signature valid)
  *   - Linux: the bytes are appended to the executable, followed by a 16 bytes footer: the payload size as a
  *     little-endian 64 bit integer and the magic `SNUNITD1`.
  */
private[snunit] object EmbeddedUnitd {
  private val footerMagic = "SNUNITD1".getBytes("US-ASCII")

  @extern private object darwin {
    def _dyld_get_image_header(index: CUnsignedInt): Ptr[Byte] = extern
    def getsectiondata(
        mhp: Ptr[Byte],
        segname: CString,
        sectname: CString,
        size: Ptr[CUnsignedLongInt]
    ): Ptr[Byte] = extern
  }

  @extern private object linux {
    def memfd_create(name: CString, flags: CUnsignedInt): CInt = extern
  }

  /** The unitd bytes, or `None` if this executable doesn't embed one. */
  def bytes(): Option[Array[Byte]] =
    if (LinktimeInfo.isMac) fromMachOSection()
    else fromAppendedPayload()

  private def fromMachOSection(): Option[Array[Byte]] = Zone {
    val size = alloc[CUnsignedLongInt]()
    val data = darwin.getsectiondata(darwin._dyld_get_image_header(0.toUInt), c"__DATA", c"__unitd", size)
    if (data == null) None
    else {
      val length = (!size).toInt
      val array = new Array[Byte](length)
      memcpy(array.atUnsafe(0), data, length.toCSize)
      Some(array)
    }
  }

  private def fromAppendedPayload(): Option[Array[Byte]] = {
    val file = new RandomAccessFile(Launcher.selfExecutable(), "r")
    try {
      val length = file.length()
      if (length < 16) None
      else {
        val footer = new Array[Byte](16)
        file.seek(length - 16)
        file.readFully(footer)
        if (!java.util.Arrays.equals(footer.slice(8, 16), footerMagic)) None
        else {
          var size = 0L
          var i = 7
          while (i >= 0) { size = (size << 8) | (footer(i) & 0xffL); i -= 1 }
          val payload = new Array[Byte](size.toInt)
          file.seek(length - 16 - size)
          file.readFully(payload)
          Some(payload)
        }
      }
    } finally file.close()
  }

  /** A path that can be executed and contains `bytes`.
    *
    * On Linux this is an anonymous in-memory file (`memfd_create`), so nothing touches the disk. Everywhere else, or if
    * memfd isn't available, it's a file in the user cache directory named after a hash of the content, so it's
    * extracted only once.
    */
  def executablePath(bytes: Array[Byte]): String =
    (if (LinktimeInfo.isLinux) tryMemfd(bytes) else None).getOrElse(extractToCache(bytes))

  private def tryMemfd(bytes: Array[Byte]): Option[String] = {
    val fd = linux.memfd_create(c"unitd", 0.toUInt)
    if (fd < 0) None
    else {
      var offset = 0
      while (offset < bytes.length) {
        val written = unistd.write(fd, bytes.atUnsafe(offset), (bytes.length - offset).toCSize)
        if (written < 0) { unistd.close(fd); return None }
        offset += written.toInt
      }
      // The fd stays open (no CLOEXEC) in this process for as long as unitd runs.
      Some(s"/proc/${unistd.getpid()}/fd/$fd")
    }
  }

  private def extractToCache(bytes: Array[Byte]): String = {
    val base = sys.env
      .get("XDG_CACHE_HOME")
      .map(Paths.get(_))
      .getOrElse(Paths.get(sys.props("user.home"), ".cache"))
    val dir = base.resolve("snunit")
    Files.createDirectories(dir)
    val target = dir.resolve(s"unitd-${fnv1a(bytes)}")
    if (!Files.exists(target) || Files.size(target) != bytes.length.toLong) {
      val tmp = Files.createTempFile(dir, "unitd", ".tmp")
      Files.write(tmp, bytes)
      Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rwxr-xr-x"))
      Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
    target.toString
  }

  private def fnv1a(bytes: Array[Byte]): String = {
    var hash = 0xcbf29ce484222325L
    var i = 0
    while (i < bytes.length) {
      hash = (hash ^ (bytes(i) & 0xffL)) * 0x100000001b3L
      i += 1
    }
    java.lang.Long.toHexString(hash)
  }
}
