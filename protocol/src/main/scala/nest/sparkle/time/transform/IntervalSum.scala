package nest.sparkle.time.transform

import com.github.nscala_time.time.Implicits.{ richReadableInstant, richReadableInterval }
import com.typesafe.config.Config
import java.util.TimeZone
import nest.sparkle.store.{ Column, Event, EventGroup }
import nest.sparkle.time.protocol.{ IntervalParameters, JsonDataStream, JsonEventWriter, KeyValueType, RangeInterval }
import nest.sparkle.time.protocol.TransformParametersJson.IntervalParametersFormat
import nest.sparkle.util.{ Period, PeriodWithZone, RecoverJsonFormat, RecoverNumeric }
import nest.sparkle.util.ConfigUtil.configForSparkle
import nest.sparkle.util.Log
import nest.sparkle.util.OptionConversion.OptionFuture
import org.joda.time.{ DateTime, DateTimeZone, Interval }
import org.joda.time.format.ISODateTimeFormat
import rx.lang.scala.Observable
import scala.Iterator
import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe._
import scala.util.{ Failure, Success, Try }
import scala.util.control.Exception.nonFatalCatch
import spire.math.Numeric
import spray.json.{ JsObject, JsonFormat }
import spray.json.DefaultJsonProtocol._
import nest.sparkle.store.EventGroup.{ OptionRows, KeyedRow }

case class StandardIntervalTransform(rootConfig: Config) extends TransformMatcher {
  override type TransformType = MultiColumnTransform
  override def prefix = "interval"
  lazy val intervalSum = new IntervalSum(rootConfig)

  override def suffixMatch = _ match {
    case "sum" => intervalSum
  }

}

case object NoColumnSpecified extends RuntimeException
case class InconsistentColumnsSpecified() extends RuntimeException
case class IncompatibleColumn(msg: String) extends RuntimeException(msg)
case class InvalidPeriod(msg: String) extends RuntimeException(msg)

/** A column transform that works on a set of source columns containing an events encoding
  * intervals as (start+duration).
  */
class IntervalSum(rootConfig: Config) extends MultiColumnTransform with Log {

  val maxParts = configForSparkle(rootConfig).getInt("transforms.max-parts")

  /** Return a json data stream that contains the total coverage of the intervals across the
    * report periods specified in the protocol request transformParameters. Coverage from each
    * source interval is calculated individually. The results coverage sums are reported
    * for each report period as an array of values for each column. e.g. for two columns:
    * [ periodStart, [coverageColumn1, coverageColumn2],
    * periodStart, [coverageColumn1, coverageColumn2],
    * ...
    * ]
    */
  override def apply  // format: OFF
      (columns: Seq[Column[_,_]], transformParameters: JsObject) 
      (implicit execution: ExecutionContext): JsonDataStream = { // format: ON

    /** try and convert protocol request transformParameters field into IntervalParameters */
    def parseParameters[T: JsonFormat](transformParameters: JsObject): Try[IntervalParameters[T]] = {
      nonFatalCatch.withTry { transformParameters.convertTo[IntervalParameters[T]] }
    }

    def withType[T]() = {
      // we recover the dynamic type here, so it's a reasonable place to start using type parameters 
      // to ensure that the calls we make are self-consistent
      val result =
        for {
          firstColumn <- columns.headOption.toTryOr(NoColumnSpecified)
          keyFormat <- RecoverJsonFormat.tryJsonFormat[T](firstColumn.keyType)
          numericKey <- RecoverNumeric.tryNumeric[T](firstColumn.keyType)
          parameters <- parseParameters[T](transformParameters)(keyFormat)
        } yield {
          val castColumns = columns.asInstanceOf[Seq[Column[T, T]]]
          summarizeColumns[T](castColumns, parameters)(numericKey, keyFormat, execution)
        }

      result.recover {
        case err =>
          JsonDataStream.error(err)
      }.get
    }

    withType()
  }

  /** summarize the intervals in each column and combine into a stream that reports both  */
  def summarizeColumns[T: Numeric: JsonFormat](columns: Seq[Column[T, T]], intervalParameters: IntervalParameters[T]) // format: OFF
    (implicit execution: ExecutionContext): JsonDataStream = { // format: ON

    val summarizedColumns =
      columns.map { column =>
        summarizeColumn(column, intervalParameters)
      }

    val rowBlocks = EventGroup.groupByKey(summarizedColumns)
    val noNoneRows = blanksToZeros(rowBlocks)

    JsonDataStream(
      dataStream = JsonEventWriter.fromObservableSeq(noNoneRows),
      streamType = KeyValueType
    )
  }

  /** convert rows with optional values into rows with zeros for None */
  private def blanksToZeros[T, U: Numeric](rowBlocks: Observable[OptionRows[T, U]]) // format: OFF
      : Observable[KeyedRow[T, U]] = { // format: ON
    val zero = implicitly[Numeric[U]].zero

    rowBlocks map { rows =>
      rows.map { row =>
        val optValues: Seq[Option[U]] = row.value
        val zeroedValues = optValues.map(_.getOrElse(zero))
        Event(row.argument, zeroedValues)
      }
    }
  }

  /** a key,value event interpreted as start, duration */
  type IntervalEvent[T] = Event[T, T]
  type IntervalEvents[T] = Seq[IntervalEvent[T]]

  /** return an optional Period wrapped in a Try, by parsing the client-protocol supplied
    * IntervalParameters.
    */
  private def periodParameter[T](parameters: IntervalParameters[T]): Try[Option[Period]] = {
    parameters.partBySize match {
      case Some(periodString) =>
        val tryResult = Period.parse(periodString).toTryOr(InvalidPeriod(s"periodString"))
        tryResult.map { Some(_) } // wrap successful result in an option (we parsed it and there was a period string)
      case None =>
        Success(None) // we parsed successfully: there was no period
    }
  }

  /** return the coverage for the interval events in this column, reporting a sequence of events, one
    * per client-protocol requested period. Each event starts at the beginning of the period and contains
    * the value of the total coverage of the interval events in that period. Overlapping portions of
    * intervals are counted only once.
    */
  private def summarizeColumn[T](column: Column[T, T], parameters: IntervalParameters[T])  // format: OFF
    (implicit execution: ExecutionContext): Observable[Seq[Event[T, T]]] = { // format: ON

    // TODO don't need to assume the that column values are the same type as the keys
    val eventRanges = SelectRanges.fetchRanges[T, T](column, parameters.ranges) // TODO dry with SummaryTransform
    implicit val keyTag = column.keyType.asInstanceOf[TypeTag[T]]
    val tryResults =
      for {
        numericKey <- RecoverNumeric.optNumeric[T](column.keyType).toTryOr(
          IncompatibleColumn(s"${column.name} doesn't contain numeric keys. Can't summarize intervals"))
        optPeriod <- periodParameter(parameters)
      } yield {
        val perRangeInitialResults =
          eventRanges.map { intervalAndEvents =>
            intervalAndEvents.events.initial.toSeq.map { initialEvents =>
              if (initialEvents.isEmpty) {
                Seq()
              } else {
                val optPeriodZone = optPeriod.map { period =>
                  //                    val timeZoneName: String = "UTC"
                  val timeZone = TimeZone.getTimeZone("America/Los_Angeles"); // TODO make this dynamic, default UTC
                  implicit val dateTimeZone = org.joda.time.DateTimeZone.forTimeZone(timeZone)

                  PeriodWithZone(period, dateTimeZone)
                }
                val iterator = summaryIterator(initialEvents, intervalAndEvents.interval, optPeriodZone)(numericKey, numericKey)
                iterator.toSeq
              }
            }
          }
        // LATER ongoing results too
        val initialResults = perRangeInitialResults.reduceLeft { (a, b) => a ++ b }
        initialResults
      }
    tryResults match {
      case Success(results) => results
      case Failure(err)     => Observable.error(err)
    }
  }

  /** Return an iterator that walks through the partitions of the events.
    *
    * Note: assumes that events are sorted by start
    */
  private def summaryIterator[T: Numeric, U: Numeric](events: Seq[Event[T, U]], rangeOpt: Option[RangeInterval[T]],
                                                      optPeriod: Option[PeriodWithZone]): Iterator[Event[T, U]] = {
    optPeriod match {
      case Some(periodWithZone) =>
        summarizeByPeriod(events, rangeOpt, periodWithZone)
      case None =>
        summarizeEntire(events)
    }
  }

  /** summarize a set of interval events into single coverage total */
  private def summarizeEntire[T: Numeric, U: Numeric]( // format: OFF
      events: Seq[Event[T, U]]
      ): Iterator[Event[T, U]] = { // format: ON
    events.headOption.map(_.argument) match {
      case Some(start) =>
        val intervals = combinedIntervals(events)
        val totalOverlaps: U = RawInterval.sumIntervals[T, U](intervals)
        val summary = Event(start, totalOverlaps)
        Iterator(summary)
      case None =>
        Iterator.empty
    }
  }

  /** convert a collection of key value Events into a collection of RawIntervals. The events
    * are interpreted as containing start,duration. The RawIntervals are merged, no two intervals
    * in the combined result overlap each other.
    */
  private def combinedIntervals[T: Numeric, U: Numeric](events: Seq[Event[T, U]]): Seq[RawInterval[T]] = {
    val intervals = events.map(RawInterval(_))
    RawInterval.combine(intervals)
  }

  /** return an iterator that walks the through the source events in period sized partitions.
    * Interprets the source events as intervals, and returns for each partition a single interval
    * representing the total overlap of the source intervals with the period partition
    */
  private def summarizeByPeriod[T: Numeric, U: Numeric]( // format: OFF
      events: Seq[Event[T, U]],
      rangeOpt: Option[RangeInterval[T]],
      periodWithZone: PeriodWithZone): Iterator[Event[T, U]] = { // format: ON
    val timePartitions = PeriodPartitioner.timePartitionsFromRequest[T,U](events, rangeOpt, periodWithZone)

    /** composite into non-overlapping intervals */
    val intervals = combinedIntervals(events)

    val activeIntervals = intervals
    // TODO remove stuff from active
    // TODO don't check stuff in active that starts after the end of this period

    timePartitions.partIterator().take(maxParts).map { jodaInterval =>
      val start: T = Numeric[Long].toType[T](jodaInterval.start.millis)
      val intersections = RawInterval.jodaMillisIntersections(activeIntervals, jodaInterval)
      val totalOverlap: U = RawInterval.sumIntervals[T, U](intersections)
      Event(start, totalOverlap)
    }
  }

  /** debug utility */
  private def dateToString(dateTime: DateTime): String = {
    dateTime.toString(ISODateTimeFormat.hourMinuteSecond.withZone(DateTimeZone.UTC))
  }

  /** debug utility */
  private def intervalToString(jodaInterval: Interval): String = {
    dateToString(jodaInterval.start) + "/" + dateToString(jodaInterval.end)
  }

}
