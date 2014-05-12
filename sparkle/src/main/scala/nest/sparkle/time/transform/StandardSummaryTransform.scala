package nest.sparkle.time.transform
import SummaryTransform.Events
import nest.sparkle.store.Event
import spire.implicits._
import StandardSummaryTransform.{sumKeyValue, withNumericKeyValue}
import spire.math._
import scala.util.Random

case class IncompatibleTransform(msg: String) extends RuntimeException(msg)

/** Built in summary transforms.  */
object StandardSummaryTransform {

  /** match a SummaryTransform string selector, and return a ColumnTransform to be applied to each source column */
  def unapply(transform: String): Option[ColumnTransform] = {
    val lowerCase = transform.toLowerCase
    if (lowerCase.startsWith("summarize")) {
      lowerCase.stripPrefix("summarize") match {
        case "max"     => Some(SummarizeMax)
        case "min"     => Some(SummarizeMin)
        case "mean"    => Some(SummarizeMean)
        case "average" => Some(SummarizeMean)
        case "random"  => Some(SummarizeRandom)
        case "sample"  => Some(SummarizeRandom)
        case "uniques" => ???
        case "sum"     => Some(SummarizeSum)
        case "count"   => ???
        case "rate"    => ???
        case _         => None
      }
    } else {
      None
    }
  }

  /** return the middle value from a sequence.  (the median if the sequence is sorted) */
  protected[transform] def middle[T](seq: IndexedSeq[T]): T = {
    require(seq.length > 0)
    seq(seq.length / 2)
  }

  protected[transform] def sumKeyValue[T:Numeric, U:Numeric](data: Events[T, U],
    columnMetadata: ColumnMetadata[T, U]): Event[T, U] = {
    data.reduceLeft{ (a, b) => Event[T, U](a.argument + b.argument, a.value + b.value) }
  }
  
  protected[transform] def withNumericKeyValue[T,U,V](metadata:ColumnMetadata[T,U]) // format: OFF
    (fn:(Numeric[T],Numeric[U])=>V):V = {
    val numericKey = metadata.optNumericKey.getOrElse {
      throw IncompatibleTransform(s"${metadata.keyType}")
    }
    val numericValue = metadata.optNumericValue.getOrElse {
      throw IncompatibleTransform(s"${metadata.valueType}")
    }

    fn(numericKey.asInstanceOf[Numeric[T]], numericValue.asInstanceOf[Numeric[U]])
  }

}

/** summarize the maximum value in each partition */
object SummarizeMax extends OneSummarizer {
  def summarizeEvents[T, U](data: Events[T, U],
    columnMetadata: ColumnMetadata[T, U]): Events[T, U] = {
    Seq(data.max(columnMetadata.valueOrderEvents))
  }
}

/** summarize the minimum value in the partition */
object SummarizeMin extends OneSummarizer {
  def summarizeEvents[T, U](data: Events[T, U],
    columnMetadata: ColumnMetadata[T, U]): Events[T, U] = {
    Seq(data.min(columnMetadata.valueOrderEvents))
  }
}

/** summarize into a single arithmetic mean value in the partition. The domain (time) of the
  * summarized result will be the mean domain of the events in the partition.
  */
object SummarizeMean extends OneSummarizer {
  def summarizeEvents[T, U](data: Events[T, U],
    columnMetadata: ColumnMetadata[T, U]): Events[T, U] = {
    withNumericKeyValue(columnMetadata) { (numericKey, numericValue) =>
      implicit val (k,v) = (numericKey, numericValue)
      val summed = sumKeyValue(data,columnMetadata)    
      val meanEvent = Event(summed.argument / data.length, summed.value / data.length)
      Seq(meanEvent)
    }
  }
}

/** summarize the sum of values in each partition.  The domain (time) of the summarized
  * value will be the mean domain of the events in the partition.
  */
object SummarizeSum extends OneSummarizer {
  def summarizeEvents[T, U](data: Events[T, U],
    columnMetadata: ColumnMetadata[T, U]): Events[T, U] = {
    
    // use the mean key (e.g. time) of the elements in the partition
    withNumericKeyValue(columnMetadata) { (numericKey, numericValue) =>
      implicit val (k,v) = (numericKey, numericValue)
      val summed = sumKeyValue(data,columnMetadata)    
      val sumEvent = Event(summed.argument / data.length, summed.value)
      Seq(sumEvent)
    }
  }
}

/** summarize the minimum value in the partition */
object SummarizeRandom extends OneSummarizer {
  def summarizeEvents[T, U](data: Events[T, U],
    columnMetadata: ColumnMetadata[T, U]): Events[T, U] = {
    require(data.length > 0)
    val pick = Random.nextInt(data.length)
    Seq(data(pick))
  }
}



