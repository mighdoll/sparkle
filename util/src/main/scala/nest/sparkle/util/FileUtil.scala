package nest.sparkle.util

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, SimpleFileVisitor, Files, Path}

object FileUtil {
  /** Remove all the files in a directory recursively.
    * @param path Directory to clean.
    */
  def cleanDirectory(path: Path) {
    if (Files.isDirectory(path)) {
      Files.walkFileTree(path, new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }
        override def postVisitDirectory(dir: Path, e: IOException): FileVisitResult = {
          e match {
            case _: IOException => throw e
            case _ =>
              Files.delete(dir)
              FileVisitResult.CONTINUE
          }
        }
      })
    }
  }

}
