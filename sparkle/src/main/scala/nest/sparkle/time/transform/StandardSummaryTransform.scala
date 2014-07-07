package nest.sparkle.time.transform
import SummaryTransform.Events
import nest.sparkle.store.Event
import spire.implicits._
import SummaryTransformUtil._
import spire.math._
import scala.util.Random

case class IncompatibleTransform(msg: String) extends RuntimeException(msg)

/** Built in summary transforms.  */
object StandardSummaryTransform extends TransformMatcher {
  override type TransformType = ColumnTransform
  override def prefix = "summarize"
  override def suffixMatch = _ match {
    case "max"     => SummarizeMax
    case "min"     => SummarizeMin
    case "mean"    => SummarizeMean
    case "average" => SummarizeMean
    case "random"  => SummarizeRandom
    case "sample"  => SummarizeRandom
    case "uniques" => ???
    case "sum"     => SummarizeSum
    case "count"   => SummarizeCount
    case "rate"    => ???
  }
}

protected[transform] object SummaryTransformUtil {
  /** return the middle value from a sequence.  (the median if the sequence is sorted) */
  protected[transform] def middle[T](seq: IndexedSeq[T]): T = {
    require(seq.length > 0)
    seq(seq.length / 2)
  }

  protected[transform] def sumKeyValue[T: Numeric, U: Numeric](data: Events[T, U]): Event[T, U] = {
    data.reduceLeft{ (a, b) => Event[T, U](a.argument + b.argument, a.value + b.value) }
  }

  protected[transform] def sumKeys[T: Numeric, U](data: Events[T, U]): T = {
    val keys = data.map{ case Event(k, v) => k }
    keys.reduceLeft { _ + _ }
  }

  protected[transform] def withNumericKeyValue[T, U, V](metadata: ColumnMetadata[T, U]) // format: OFF
    (fn:(Numeric[T],Numeric[U])=>V):V = { // format: ON
    withNumericKey(metadata) { numericKey =>
      withNumericValue(metadata) { numericValue =>
        fn(numericKey.asInstanceOf[Numeric[T]], numericValue.asInstanceOf[Numeric[U]])
      }
    }
  }

  protected[transform] def withNumericKey[T, U, V](metadata: ColumnMetadata[T, U]) // format: OFF
    (fn:Numeric[T]=>V):V = { // format: ON
    val numericKey = metadata.optNumericKey.getOrElse {
      throw IncompatibleTransform(s"${metadata.keyType}")
    }

    fn(numericKey.asInstanceOf[Numeric[T]])
  }

  protected[transform] def withNumericValue[T, U, V](metadata: ColumnMetadata[T, U]) // format: OFF
    (fn:Numeric[U]=>V):V = { // format: ON
    val numericValue = metadata.optNumericValue.getOrElse {
      throw IncompatibleTransform(s"${metadata.valueType}")
    }

    fn(numericValue.asInstanceOf[Numeric[U]])
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
    require(data.length > 0)
    withNumericKeyValue(columnMetadata) { (numericKey, numericValue) =>
      implicit val (k, v) = (numericKey, numericValue)
      val summed = sumKeyValue(data)
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
    require(data.length > 0)

    // use the mean key (e.g. time) of the elements in the partition
    withNumericKeyValue(columnMetadata) { (numericKey, numericValue) =>
      implicit val (k, v) = (numericKey, numericValue)
      val summed = sumKeyValue(data)
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

/** summarize the minimum value in the partition */
object SummarizeCount extends OneSummarizer {
  def summarizeEvents[T, U](data: Events[T, U],
                            columnMetadata: ColumnMetadata[T, U]): Events[T, U] = {
    require(data.length > 0)
    withNumericKeyValue(columnMetadata) { (numericKey, numericValue) =>
      implicit val (_k, _v) = (numericKey, numericValue)
      val key = sumKeys(data) / data.length
      val value = numericValue.fromInt(data.length)
      Seq(Event(key, value)) // TODO this should return values of type Int, not type U..
    }
  }
}

