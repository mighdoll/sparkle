package nest.sparkle.tools

import rx.lang.scala.Observable
import nest.sparkle.util.RecoverJsonFormat
import rx.lang.scala.Subject

trait TsvExporter extends Exporter {
  
  /** return an Observable that emits tab separated value formatted strings
   *  based on a Tabular source.  The first string will be a header, and there
   *  will be one subsequent line for each row in the tabular.
   */
  def tsvLines(tabular:Tabular):Observable[String] = {
    emitHeader(tabular) ++ emitRows(tabular)
  }
  
  
  /** write the header of the .tsv file */
  private def emitHeader(tabular: Tabular): Observable[String] = {
    val headers = tabular.columns.map { case NameAndType(name, typed) => name }
    val tabbedHeaders = headers.mkString("", "\t", "\n")
    Observable.from(Seq(tabbedHeaders))
  }

  /** write all the rows in a .tsv file */
  private def emitRows(tabular: Tabular): Observable[String] = {
    val formatters =
      tabular.columns.map {
        case NameAndType(name, typed) =>
          RecoverJsonFormat.tryJsonFormat[Any](typed).get
      }

    val subj = Subject[String]()
    tabular.rows.map { rows:Seq[Row] =>
      rows.foreach { row =>
        val stringValues = row.values.zip(formatters) map {
          case (Some(value), formatter) => formatter.write(value).toString
          case (None, _)                => ""
        }
        val line = stringValues.mkString("", "\t", "\n")
        subj.onNext(line)
      }
      subj.onCompleted()
    }
    subj
  }

}