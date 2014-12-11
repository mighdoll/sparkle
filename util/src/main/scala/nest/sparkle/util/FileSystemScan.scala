package nest.sparkle.util

import scala.collection.mutable
import scala.collection.JavaConverters._

import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

object FileSystemScan {

  private val maxDirectoryDepth = 1000

  /** Scan a filesystem subtree and report directories and matching files */
  protected[util]
  def scanFileSystem(path: Path, glob: String = "**"): (mutable.ArrayBuffer[Path], mutable.ArrayBuffer[Path]) = {
    val files = mutable.ArrayBuffer[Path]()
    val directories = mutable.ArrayBuffer[Path]()
    if (!Files.isDirectory(path)) throw new IllegalArgumentException

    /** a java nio simple file visitor that matches files using glob syntax */
    class MatchingVisitor(glob: String) extends SimpleFileVisitor[Path] {

      val pathMatcher = FileSystems.getDefault.getPathMatcher("glob:" + glob)
      override def visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult = {
        if (pathMatcher.matches(path)) {
          files.append(path)
        }
        FileVisitResult.CONTINUE
      }

      override def preVisitDirectory(path:Path, attrs:BasicFileAttributes):FileVisitResult = {
        directories.append(path)
        FileVisitResult.CONTINUE
      }
    }

    val visitor = new MatchingVisitor(glob)
    Files.walkFileTree(path, Set(FileVisitOption.FOLLOW_LINKS).asJava, maxDirectoryDepth, visitor)
    val relativeFiles = files.map {path.relativize(_)}
    (relativeFiles, directories)
  }

}
