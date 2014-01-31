package nest.sparkle.loader

import java.nio.file.Path
import java.nio.file.Files

/** Identify .tsv and .csv files by suffix */
object ParseableFile {

  /** Extractor that returns the FileFormat for a file that we recognize as parseable */
  def unapply(path: Path): Option[FileFormat] = {
    path.getFileName().toString match {
      case ParseableSuffix(format) => Some(format)
      case _                       => None
    }
  }

  /** extract the .suffix from a string */
  private val Suffix = ".*[.](.*)"r

  object ParseableSuffix {
    /** Extractor that matches if a fileName has a suffix that we recognize as a parseable file type */
    def unapply(fileName: String): Option[FileFormat] = {
      fileName match {
        case Suffix(suffix) => matchSuffix(suffix)
        case _              => None
      }
    }

    /** Return the FileFormat for a suffix */
    private def matchSuffix(suffix: String): Option[FileFormat] = {
      suffix.toLowerCase match {
        case "csv" => Some(CsvFormat)
        case "tsv" => Some(TsvFormat)
        case _     => None
      }
    }

  }
}

sealed abstract class FileFormat
case object CsvFormat extends FileFormat
case object TsvFormat extends FileFormat
case object UnknownFormat extends FileFormat
