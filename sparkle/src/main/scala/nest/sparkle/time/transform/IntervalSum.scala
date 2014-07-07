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

  def parseParameters[T: JsonFormat](transformParameters: JsObject): Try[IntervalParameters[T]] = {
    nonFatalCatch.withTry { transformParameters.convertTo[IntervalParameters[T]] }
  }

  def summarizeColumn[T, U](column: Column[T, U], parameters: IntervalParameters[T])  // format: OFF
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

  import com.github.nscala_time.time.Imports._
  import org.joda.time.{ Period => JodaPeriod }
  import spire.implicits._

  /** assumes that events are sorted by start */
  def partitionIterator[T: Numeric, U: Numeric](events: Seq[Event[T, U]], rangeOpt: Option[RangeInterval[T]],
                                                period: nest.sparkle.util.Period): Iterator[Event[T, U]] = {
    // TODO DRY me with SummaryTransform!
    // TODO make configurable based on storage time type..

    val range = rangeOpt.getOrElse(RangeInterval())
    val start = range.start.getOrElse(events.head.argument)
    val (end, includeEnd) = range.until match {
      case Some(explicitEnd) => (explicitEnd, false)
      case None              => (events.last.argument, true)
    }
    val timeZone = TimeZone.getTimeZone("America/Los_Angeles");
    val dateTimeZone = org.joda.time.DateTimeZone.forTimeZone(timeZone)

    val startDate = new DateTime(start, dateTimeZone)
    val endDate = new DateTime(end, dateTimeZone)

    val periodType = PeriodType.forFields(Array(period.durationType))
    val partPeriod = new JodaPeriod(period.value.toLong, periodType)

    case class DatedInterval(event: Event[T, U]) {
      val start = new DateTime(event.argument, dateTimeZone)
      val period = JodaPeriod.millis(event.value.toInt)
      val end = start + period

      /** calculate the overlap with a target period */
      def overlap(targetStart: DateTime, targetEnd: DateTime): U = {
        if (start >= targetStart && end < targetEnd) { // totally within target
          event.value
        } else if (start < targetStart && end > targetStart && end < targetEnd) { // starts before target, ends within
          val tooEarly = new JodaPeriod(start, targetStart)
          event.value - tooEarly.getMillis
        } else if (start < targetStart && end >= targetEnd) { // starts before target ends after
          val tooEarly = new JodaPeriod(start, targetStart)
          val tooLate = new JodaPeriod(end, targetEnd)
          event.value - (tooEarly.getMillis + tooLate.getMillis)
        } else if (start > targetStart && end >= targetEnd) { // starts within, ends after
          val tooLate = new JodaPeriod(end, targetEnd)
          event.value - tooLate.getMillis
        } else {
          implicitly[Numeric[U]].zero
        }
      }
    }

    val datedEvents = events.map(DatedInterval(_))

    var remaining = datedEvents.filter(_.start >= startDate) // filter in case the source data contains some extras
    var partStart = startDate

    /** iterate through the time periods, returning a Seq containing a single entry per period */
    new Iterator[Event[T, U]] {
      override def hasNext(): Boolean = !remaining.isEmpty && (partStart < endDate || (partStart == end && includeEnd))
      override def next(): Event[T, U] = {
        val partEnd = partStart + partPeriod

        val overlap = remaining.map{ dated => dated.overlap(partStart, partEnd) }
        val totalOverlap = overlap.reduceLeft(_ + _)
        val result = Event(partStart.getMillis.asInstanceOf[T], totalOverlap)

        val remainder = remaining.filterNot { dated =>
          dated.end <= partEnd
        }
        remaining = remainder
        partStart = partStart + partPeriod
        result
      }
    }

  }
}