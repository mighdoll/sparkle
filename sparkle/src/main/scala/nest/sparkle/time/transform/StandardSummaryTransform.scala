package nest.sparkle.time.transform
import SummaryTransform.Events

/** Built in summary transforms.  */
object StandardSummaryTransform {
  
  /** match a SummaryTransform string selector, and return a ColumnTransform to be applied to each source column */
  def unapply(transform: String): Option[ColumnTransform] = {
    val lowerCase = transform.toLowerCase
    if (lowerCase.startsWith("summarize")) {
      lowerCase.stripPrefix("summarize") match {
        case "max"     => Some(SummarizeMax)
        case "min"     => Some(SummarizeMin)
        case "linear"  => ???
        case "mean"    => ???
        case "average" => ???
        case "random"  => ???
        case "sample"  => ???
        case "uniques" => ???
        case "sum"     => ???
        case "count"   => ???
        case "rate"    => ???
        case _         => None
      }
    } else {
      None
    }
  }
}

/** summarize the maximum value in each partition */
object SummarizeMax extends OneSummarizer {
  def summarizeEvents[T, U](data: Events[T, U],
                            columnMetadata: ColumnMetadata[T, U]): Events[T, U] = {
    Seq(data.max(columnMetadata.valueOrderEvents))
  }
}

/** summarize the minimum value in each partition */
object SummarizeMin extends OneSummarizer {
  def summarizeEvents[T, U](data: Events[T, U],
                            columnMetadata: ColumnMetadata[T, U]): Events[T, U] = {
    Seq(data.min(columnMetadata.valueOrderEvents))
  }
}

