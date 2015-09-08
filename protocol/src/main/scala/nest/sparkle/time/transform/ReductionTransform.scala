package nest.sparkle.time.transform

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.{Success, Try}
import scala.reflect.runtime.universe._

import com.typesafe.config.Config
import spray.json.JsObject

import org.joda.time.{DateTimeZone, DurationFieldType}

import nest.sparkle.measure.{Milliseconds, Measurements, Span, TraceId}
import nest.sparkle.store.Column
import nest.sparkle.time.protocol.{SummaryParameters, JsonDataStream, JsonEventWriter, KeyValueType}
import nest.sparkle.time.transform.FetchStreams.fetchData
import nest.sparkle.util._
import nest.sparkle.datastream._
import nest.sparkle.util.TryToFuture._
import spire.math._
import spire.implicits._

/** support for matching the "transform" field in protocol requests for the reduction transforms */
case class ReductionTransform(rootConfig: Config)(implicit measurements: Measurements) extends TransformMatcher {
  override type TransformType = MultiTransform
  override def prefix = "reduce"

  lazy val sumTransform = makeNumericTransform { ReduceSum()(_) }
  lazy val meanTransform = makeNumericTransform { ReduceMean()(_) }
  lazy val minTransform = makeNumericTransform { ReduceMin()(_) }
  lazy val maxTransform = makeNumericTransform { ReduceMax()(_) }
  lazy val countTransform = makeNumericTransform { ReduceCount()(_) }

  /** create a ReduceTransform for a particular numeric reduction operation */
  def makeNumericTransform
      ( reductionFactory: Numeric[Any] => IncrementalReduction[Any] )
      : ReduceTransform[_] = {

    def produceReducer: TypeTag[_] => Try[IncrementalReduction[Any]] = { typeTag:TypeTag[_] =>
      RecoverNumeric.tryNumeric[Any](typeTag).map { numericValue =>
        reductionFactory(numericValue)
      }
    }

    ReduceTransform[Any](rootConfig, produceReducer)
  }

  override def suffixMatch: PartialFunction[String, TransformType] = _ match {
    case "sum"      => sumTransform
    case "mean"     => meanTransform
    case "average"  => meanTransform
    case "min"      => minTransform
    case "max"      => maxTransform
    case "count"    => countTransform
  }
}

/** Support for executing a reduction transform (e.g. Sum or Mean). */
case class ReduceTransform[V]
    ( rootConfig: Config,
      produceReduction: TypeTag[_] => Try[IncrementalReduction[V]] )
    ( implicit measurements: Measurements )
    extends MultiTransform with Log {

  private val validator = ValidateReductionParameters()
  private val noGrouping = StreamGrouping(maxParts = maxParts, None)

  override def transform // format: OFF
      ( futureGroups: Future[Seq[ColumnGroup]], transformParameters: JsObject)
      ( implicit execution: ExecutionContext, traceId: TraceId)
      : Future[Seq[JsonDataStream]] = {

    implicit val span = Span.prepareRoot("Sum", traceId).start()

    def withFixedKeyType[K]: Future[Seq[JsonDataStream]] = {
      for {
        ValidReductionParameters(
          keyType, keyJsonFormat, keyOrdering, reductionParameters, grouping, ongoingDuration
        ) <- validator.validate[K](futureGroups, transformParameters)
        data <- fetchData[K,V](futureGroups, reductionParameters.ranges, None, Some(span))
        reduced = reduceOperation[K](data, produceReduction, grouping, ongoingDuration)
      } yield {
        reduced.allStreams.map { stream =>
          val json = JsonEventWriter.fromDataStream(stream, span)
          val jsonSeq = json.map(_.toVector) // LATER switch to array for a bit of extra perf
          JsonDataStream(
            dataStream = jsonSeq,
            streamType = KeyValueType
          )
        }
      }
    }
    withFixedKeyType[Any]
  }

  /** Perform a reduce operation on all the streams in a group set.  */
  private def reduceOperation[K] // format: OFF
      ( groupSet: StreamGroupSet[K, V, AsyncWithRangeColumn],
        makeReduction: TypeTag[_] => Try[IncrementalReduction[V]],
        requestGrouping: Option[RequestGrouping],
        ongoingDuration: Option[FiniteDuration] )
      ( implicit execution: ExecutionContext, parentSpan: Span )
      : StreamGroupSet[K, Option[V], AsyncWithRange] = { // format: ON

    groupSet.mapStreams { stream =>
      implicit val keyType = stream.keyType
      implicit val valueType = stream.valueType

      val reduced: Future[AsyncWithRange[K,Option[V]]] = {
        val futureGrouping =
          streamGrouping(requestGrouping, stream.self.requestRange, stream.self.column)
        for {
          reduction <- makeReduction(valueType).toFuture
          reduced <- stream.self.flexibleReduce(futureGrouping, reduction, ongoingDuration)
        } yield {
          reduced
        }
      }
      AsyncWithRange.flattenFutureAsyncWithRange(reduced, stream.self.requestRange)
    }
  }


  /** Convert any difficult (Into*) partitioning into simple (By*) partitionings.
    * e.g. convert IntoCountedParts into ByCount by fetching the total number of
    * items in the column and dividing by the target number of parts
    *
    * Clients can specify any of the groupings that subclass RequestGrouping which includes
    * more groupings than the stream reduction code can handle. The stream reduction
    * only supports two groupings: by date and by count. This routine converts the more complex
    * groupings that require extra fetches from the database (such as IntoCountedParts) into
    * simpler ones e.g. ByCount.  */
  private def streamGrouping[K: TypeTag]
      ( requestGrouping:Option[RequestGrouping],
        requestRange: Option[SoftInterval[K]],
        column:Column[K,V] )
      ( implicit execution: ExecutionContext, parentSpan: Span )
      : Future[StreamGrouping] = {
    requestGrouping match {
      case Some(grouping@ByCount(count)) =>
        Future.successful(StreamGrouping(maxParts, Some(grouping)))
      case Some(grouping@ByDuration(_, _)) =>
        Future.successful(StreamGrouping(maxParts, Some(grouping)))
      case None =>
        Future.successful(noGrouping)
      case Some(IntoCountedParts(count)) =>
        countedPartsToByCount(count, requestRange, column).map { byCount =>
          StreamGrouping(maxParts, Some(byCount))
        }
      case Some(IntoDurationParts(count, emitEmpties)) =>
        RecoverNumeric.tryNumeric[K](typeTag[K]).toFuture.flatMap { implicit numericKey =>
          durationPartsToByDuration(count, emitEmpties, requestRange, column).map { byDuration =>
            StreamGrouping(maxParts, Some(byDuration))
          }
        }
    }
  }

  /** Convert IntoCountedParts into a ByCount reduction and attach it to the streams.
    * The approach is to request the total count from the database and then issue a ByCount
    * reduction request with target_per_part_count = total_count / target_number_of_parts */
  private def countedPartsToByCount[K]
      ( intoParts:Long, requestRange:Option[SoftInterval[K]], column:Column[K,V] )
      ( implicit execution: ExecutionContext, parentSpan: Span )
      : Future[ByCount] = {

    val optStart = requestRange.flatMap(_.start)
    val optUntil = requestRange.flatMap(_.until)
    val futureGrouping =
      column.countItems(optStart, optUntil).map { totalCount =>
        val countPerPart = math.ceil(totalCount.toDouble / intoParts).toInt
        ByCount(countPerPart)
      }
    futureGrouping
  }

  // TODO consider fetching data for the rounded period, lest the first and last parts might be too small

  /** Convert IntoDurationParts into a ByDuration reduction and attach it to the streams.
    * The approach is to request the total duration from the database and then issue a ByDuration
    * reduction request with the appropriately rounded time duration. */
  private def durationPartsToByDuration[K: Numeric]
      ( intoParts:Long, emitEmpties:Boolean,
        requestRange:Option[SoftInterval[K]], column:Column[K,V] )
      ( implicit execution: ExecutionContext, parentSpan: Span )
      : Future[ByDuration] = {

    val requestStart = requestRange.flatMap(_.start)
    val requestUntil = requestRange.flatMap(_.until)

    // if no range is specified, and the column doesn't have two keys use this as byDuration
    val defaultDuration = Period(1, DurationFieldType.minutes)

    // get start and until, fetching from db if not provided by the request
    val futureStart:Future[Option[K]] = requestStart.map{ start =>
      Future.successful(Some(start))
    }.getOrElse {
      column.firstKey
    }
    val futureUntil:Future[Option[K]] = requestUntil.map{ until =>
      Future.successful(Some(until))
    }.getOrElse {
      column.lastKey.map { lastKey => lastKey.map (_ + 1)} // until is one past the last key
    }

    val futureDuration:Future[Period] =
      for {
        optStart <- futureStart
        optUntil <- futureUntil
      } yield {
        (optStart, optUntil) match {
          case (Some(start), Some(until)) =>
            val intervalMillis = until.toLong - start.toLong
            val partMillis = intervalMillis / intoParts
            val result = IntervalToPeriod.millisToRoundedPeriod(Milliseconds(partMillis))
            result
          case _ =>
            defaultDuration
        }
      }

    futureDuration.map{ period =>
      val periodWithZone = PeriodWithZone(period, DateTimeZone.UTC)
      ByDuration(periodWithZone, emitEmpties)
    }
  }


}
