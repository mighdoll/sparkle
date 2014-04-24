/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.util

import scala.collection.mutable
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import scala.collection.JavaConverters._

object FileSystemScan {
  
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
    Files.walkFileTree(path, Set(FileVisitOption.FOLLOW_LINKS).asJava, 1000, visitor)
    val relativeFiles = files.map {path.relativize(_)}
    (relativeFiles, directories)
  }
  
}
