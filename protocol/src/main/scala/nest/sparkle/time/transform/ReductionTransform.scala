package nest.sparkle.time.transform

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.{Failure, Success, Try}
import scala.reflect.runtime.universe._

import com.typesafe.config.Config
import spray.json.JsObject
import nest.sparkle.measure.{Measurements, Span, TraceId}
import nest.sparkle.time.protocol.{SummaryParameters, JsonDataStream, JsonEventWriter, KeyValueType}
import nest.sparkle.time.transform.FetchStreams.fetchData
import nest.sparkle.util.{Log, PeriodWithZone, RecoverNumeric}
import nest.sparkle.datastream._
import spire.math.Numeric

/** support protocol "transform" field matching for the reduction transforms */ 
case class ReductionTransform(rootConfig: Config)(implicit measurements: Measurements) extends TransformMatcher {
  override type TransformType = MultiTransform
  override def prefix = "reduce"

  lazy val sumTransform = makeNumericTransform { ReduceSum()(_) }
  lazy val meanTransform = makeNumericTransform { ReduceMean()(_) }
  lazy val minTransform = makeNumericTransform { ReduceMin()(_) }
  lazy val maxTransform = makeNumericTransform { ReduceMax()(_) }

  def makeNumericTransform
      ( reductionFactory: Numeric[Any] => Reduction[Any] )
      : ReduceTransform[_] = {

    def produceReducer: TypeTag[_] => Try[Reduction[Any]] = { typeTag:TypeTag[_] =>
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
  }
}

case class ReduceTransform[V]
    ( rootConfig: Config,
      produceReduction: TypeTag[_] => Try[Reduction[V]] )
    ( implicit measurements: Measurements )
  extends MultiTransform with Log {

  private val validator = ValidateReductionParameters()

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
        reduced = reduceOperation[K](produceReduction, data, grouping, ongoingDuration)
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

  def fetchBoundsIfNecessary[K]
      ( streams: StreamGroupSet[K, V, AsyncWithRange], groupingType: GroupingType, futureGroups:Future[Seq[ColumnGroup]] )
      ( implicit execution: ExecutionContext, traceId: TraceId)
      : Future[GroupingType] = {
    groupingType match {
      case IntoCountedParts(count) =>
//        streams.mapStreams { stream =>
//          FetchRanges.fetchRange()
//        }
//        futureGroups
//        ByCount(elementCount)
      case _ =>
    }
    ???
  }

  /** Perform a reduce operation on all the streams in a group set.
    */
  def reduceOperation[K] // format: OFF  
      ( makeReduction: TypeTag[_] => Try[Reduction[V]],
        groupSet: StreamGroupSet[K, V, AsyncWithRange],
        reductionGrouping: Option[GroupingType],
        ongoingDuration: Option[FiniteDuration] )
      ( implicit execution: ExecutionContext, parentSpan: Span )
      : StreamGroupSet[K, Option[V], AsyncWithRange] = { // format: ON

    groupSet.mapStreams { stream =>
      implicit val keyType = stream.keyType
      implicit val valueType = stream.valueType

      val reduced: Try[TwoPartStream[K, Option[V], AsyncWithRange]] = {
        for {
          reduction <- makeReduction(valueType)
        } yield {
          val grouping = ReductionGrouping(
            maxParts = maxParts,
            grouping = reductionGrouping
          )
          stream.self.flexibleReduce(grouping, reduction, ongoingDuration)
        }
      }

      reduced match {
        case Success(reducedStream) => reducedStream
        case Failure(err) => AsyncWithRange.error[K, Option[V]](err, stream.self.requestRange)
      }
    }
  }

}
