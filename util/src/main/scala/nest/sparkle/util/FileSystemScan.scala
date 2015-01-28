package nest.sparkle.util

import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._

import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

object FileSystemScan {

  private val maxDirectoryDepth = 1000

  /** Scan a filesystem subtree and report matching files */
  def scanForFiles(path:Path):Seq[Path] = {
    val (files,dirs) = scanFileSystem(path)
    files.toVector
  }

  /** Scan a filesystem subtree and report directories and matching files */
  protected[util]
  def scanFileSystem(path: Path, glob: String = "**"): (ArrayBuffer[Path], ArrayBuffer[Path]) = {
    if (!Files.isDirectory(path)) {
      (ArrayBuffer(path), ArrayBuffer())
    } else {
      val files = ArrayBuffer[Path]()
      val directories = ArrayBuffer[Path]()

      /** a java nio simple file visitor that matches files using glob syntax */
      class MatchingVisitor(glob: String) extends SimpleFileVisitor[Path] {

        val pathMatcher = FileSystems.getDefault.getPathMatcher("glob:" + glob)

        override def visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult = {
          if (pathMatcher.matches(path)) {
            files.append(path)
          }
          FileVisitResult.CONTINUE
        }

        override def preVisitDirectory(path: Path, attrs: BasicFileAttributes): FileVisitResult = {
          directories.append(path)
          FileVisitResult.CONTINUE
        }
      }

      val visitor = new MatchingVisitor(glob)
      Files.walkFileTree(path, Set(FileVisitOption.FOLLOW_LINKS).asJava, maxDirectoryDepth, visitor)
      val relativeFiles = files.map {
        path.relativize(_)
      }
      (relativeFiles, directories)
    }
  }

}
