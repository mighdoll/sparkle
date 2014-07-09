package nest.sparkle.time.transform

import spray.json.JsObject
import nest.sparkle.store.Column
import scala.concurrent.ExecutionContext
import nest.sparkle.time.protocol.JsonDataStream
import scala.util.Try
import spray.json.DefaultJsonProtocol._
import nest.sparkle.time.protocol.TransformParametersJson.IntervalParametersFormat
import scala.util.control.Exception._
import spray.json._
import nest.sparkle.time.protocol.IntervalParameters
import nest.sparkle.util.RecoverJsonFormat
import rx.lang.scala.Observable
import scala.reflect.runtime.universe._
import nest.sparkle.store.Event
import nest.sparkle.util.RecoverNumeric
import nest.sparkle.util.OptionConversion._
import nest.sparkle.time.protocol.JsonEventWriter
import nest.sparkle.time.protocol.KeyValueType
import spire.math.Numeric
import nest.sparkle.time.protocol.RangeInterval
import nest.sparkle.util.ObservableFuture._
import nest.sparkle.util.Period
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.util.TimeZone
import org.joda.time.PeriodType
import scala.util.Success
import scala.util.Failure
import com.github.nscala_time.time.Implicits._
import org.joda.time.{ Period => JodaPeriod }
import spire.implicits._
import org.joda.time.Interval

object StandardIntervalTransform extends TransformMatcher {
  override type TransformType = ColumnTransform
  override def prefix = "interval"
  def suffixMatch = _ match {
    case "sum" => IntervalSum
  }
}

case class IncompatibleColumn(msg: String) extends RuntimeException(msg)
case class InvalidPeriod(msg: String) extends RuntimeException(msg)

/** A column transform that works on a single source column containing an interval
  */
object IntervalSum extends ColumnTransform {
  override def apply[T, U]  // format: OFF
      (column: Column[T, U], transformParameters: JsObject) 
      (implicit execution: ExecutionContext): JsonDataStream = { // format: ON

    implicit val keyFormat = RecoverJsonFormat.jsonFormat[T](column.keyType)
    implicit val valueFormat = keyFormat.asInstanceOf[JsonFormat[U]]

    val result =
      for {
        parameters <- parseParameters(transformParameters)(keyFormat)
      } yield {
        val summarized = summarizeColumn(column, parameters)
        JsonDataStream(
          dataStream = JsonEventWriter.fromObservableSeq(summarized),
          streamType = KeyValueType
        )
      }

    result.recover { case err => JsonDataStream.error(err) }.get
  }

  private def parseParameters[T: JsonFormat](transformParameters: JsObject): Try[IntervalParameters[T]] = {
    nonFatalCatch.withTry { transformParameters.convertTo[IntervalParameters[T]] }
  }

  private def summarizeColumn[T, U](column: Column[T, U], parameters: IntervalParameters[T])  // format: OFF
    (implicit execution: ExecutionContext): Observable[Seq[Event[T, U]]] = { // format: ON

    val eventRanges = SelectRanges.fetchRanges(column, parameters.ranges) // TODO dry with SummaryTransform
    implicit val keyTag = column.keyType.asInstanceOf[TypeTag[T]]
    val tryResults =
      for {
        numericKey <- RecoverNumeric.optNumeric[T](column.keyType).toTryOr(
          IncompatibleColumn(s"${column.name} doesn't contain numeric keys. Can't summarize intervals"))
        numericValue <- RecoverNumeric.optNumeric[U](column.valueType).toTryOr(
          IncompatibleColumn(s"${column.name} doesn't contain numeric values. Can't summarize intervals"))
        periodString = parameters.partSize.getOrElse (???)
        period <- Period.parse(periodString).toTryOr(
          InvalidPeriod(s"periodString"))
      } yield {
        val perRangeInitialResults =
          eventRanges.map { intervalAndEvents =>
            intervalAndEvents.events.initial.toSeq.map { initialEvents =>
              if (initialEvents.isEmpty) {
                Seq()
              } else {
                val iterator = partitionIterator(initialEvents, intervalAndEvents.interval, period)(numericKey, numericValue)
                iterator.toSeq
              }
            }
          }
        // LATER ongoing results too
        val initialResults = perRangeInitialResults.reduceLeft{ (a, b) => a ++ b }
        initialResults
      }
    tryResults match {
      case Success(results) => results
      case Failure(err)     => Observable.error(err)
    }
  }

  /** assumes that events are sorted by start */
  private def partitionIterator[T: Numeric, U: Numeric](events: Seq[Event[T, U]], rangeOpt: Option[RangeInterval[T]],
                                                        period: nest.sparkle.util.Period): Iterator[Event[T, U]] = {
    // TODO DRY me with SummaryTransform!
    // TODO make configurable based on storage time type..
    // TODO consider where to do local time conversion, probably take a timezone parameter to IntervalSum..

    val range = rangeOpt.getOrElse(RangeInterval())
    val start = range.start.getOrElse(events.head.argument)
    val (end, includeEnd) = range.until match {
      case Some(explicitEnd) => (explicitEnd, false)
      case None              => (endOfLatestInterval(events), true)
    }
    val timeZone = TimeZone.getTimeZone("America/Los_Angeles"); // TODO make this dynamic
    implicit val dateTimeZone = org.joda.time.DateTimeZone.forTimeZone(timeZone)

    val endDate = new DateTime(end)
    val startDate = {
      val baseStartDate = new DateTime(start, dateTimeZone)
      period.roundDate(baseStartDate)
    }

    val periodType = PeriodType.forFields(Array(period.durationType))
    val partPeriod = period.toJoda

    val datedEvents = events.map(DatedInterval(_))

    var remaining = datedEvents
    var partStart = startDate

    /** iterate through the time periods, returning a Seq containing a single entry per period */
    new Iterator[Event[T, U]] {
      override def hasNext(): Boolean = !remaining.isEmpty && (partStart < endDate || (partStart == endDate && includeEnd))
      override def next(): Event[T, U] = {
        //        remaining = oldIntervalsRemoved()
        val partEnd = partStart + partPeriod
        val overlap = remaining.map{ dated => dated.overlap(partStart, partEnd) }
        val totalOverlap = overlap.reduceLeft(_ + _)
        val result = Event(partStart.getMillis.asInstanceOf[T], totalOverlap)
//        println(s"Interval.iterator.next:  start:$partStart  until:$partEnd  totalOverlap:$totalOverlap  result: $result")
        assert(partStart + partPeriod > partStart)
        partStart = partStart + partPeriod

        result
      }

      private def oldIntervalsRemoved(): Seq[DatedInterval[T, U]] = {
        val remainder = remaining.filterNot { dated =>
          dated.end <= partStart
        }
        remainder
      }
    }
  }

  private def endOfLatestInterval[T: Numeric, U: Numeric](events: Seq[Event[T, U]]): T = {
    val numericKey = implicitly[Numeric[T]]
    val ends = events.map { case Event(key, value) => key.toLong + value.toLong }
    numericKey.fromLong(ends.max)
  }

}

/** a wrapper around a millisecond-denominated Event interval, with utilities for converting to Joda dates
  * and calculating overlaps in Joda time.
  */
case class DatedInterval[T: Numeric, U: Numeric](event: Event[T, U])(implicit dateTimeZone: DateTimeZone) {
  private val numericValue = implicitly[Numeric[U]]
  val start = new DateTime(event.argument, dateTimeZone)

  val end = {
    val endMillis = start.millis + event.value.toLong
    new DateTime(endMillis, dateTimeZone)
  }

  /** calculate the overlap with a target period */
  def overlap(targetStart: DateTime, targetEnd: DateTime): U = {
//    println(s"DatedInterval.overlap:  targetStart: $targetStart targetEnd: $targetEnd")
//    println(s"DatedInterval:                start: $start             end: $end")
    if (start >= targetEnd || end <= targetStart) {
//      println("- no overlap")
      numericValue.zero
    } else if (start >= targetStart && end < targetEnd) { // totally within target
//      println(" - totally within")
      event.value
    } else if (start < targetStart && end > targetStart && end < targetEnd) { // starts before target, ends within
//      println("- starts before, ends within")
      val tooEarly = targetStart.millis - start.millis
      val longResult = event.value.toLong - tooEarly
      numericValue.fromLong(longResult)
    } else if (start < targetStart && end >= targetEnd) { // starts before target ends after
//      println("- starts before, ends after")
      val tooEarly = targetStart.millis - start.millis
      val tooLate = targetEnd.millis - end.millis
      val longResult = event.value.toLong - (tooEarly + tooLate)
      numericValue.fromLong(longResult)
    } else if (start >= targetStart && start < targetEnd && end >= targetEnd) { // starts within, ends after
//      println("- starts within, ends after")
      val tooLate = end.getMillis - targetEnd.getMillis
      val longResult = event.value.toLong - tooLate
      numericValue.fromLong(longResult)
    } else {
//      println("- no overlap, fall through case. why?")
      numericValue.zero
    }
  }
}
