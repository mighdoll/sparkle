package nest.sparkle.time.transform

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.{Failure, Success, Try}
import scala.reflect.runtime.universe._

import com.typesafe.config.Config
import spray.json.JsObject
import nest.sparkle.measure.{Measurements, Span, TraceId}
import nest.sparkle.time.protocol.{JsonDataStream, JsonEventWriter, KeyValueType}
import nest.sparkle.time.transform.FetchStreams.fetchData
import nest.sparkle.util.{Log, PeriodWithZone, RecoverNumeric}
import nest.sparkle.datastream._
import spire.math.Numeric

/** support protocol "transform" field matching for the reduction transforms */ 
case class ReductionTransform(rootConfig: Config)(implicit measurements: Measurements) extends TransformMatcher {
  override type TransformType = MultiTransform
  override def prefix = "reduce"

  lazy val sumTransform = makeNumericTransform[Any]{ ReduceSum()(_) }
  lazy val minTransform = makeNumericTransform[Any]{ ReduceMin()(_) }
  lazy val maxTransform = makeNumericTransform[Any]{ ReduceMax()(_) }

  def makeNumericTransform[V](reduction:Numeric[V] => Reduction[V]): ReduceTransform[V] = {
    def produceReducer: TypeTag[_] => Try[Reduction[V]] = {typeTag:TypeTag[_] =>
      RecoverNumeric.tryNumeric[V](typeTag).map { numericValue =>
        reduction(numericValue)
      }
    }

    ReduceTransform(rootConfig, produceReducer)
  }

  override def suffixMatch = _ match {
    case "sum" => sumTransform
    case "min" => minTransform
    case "max" => maxTransform
  }
}

case class ReduceTransform[V](rootConfig: Config, produceReduction: TypeTag[_] => Try[Reduction[V]])
                          (implicit measurements: Measurements)
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
        keyType, keyJsonFormat, keyOrdering, reductionParameters, periodSize, ongoingDuration
        ) <- validator.validate[K](futureGroups, transformParameters)
        data <- fetchData[K,V](futureGroups, reductionParameters.ranges, None, Some(span))
        reduced = reduceOperation[K,V](produceReduction, data, reductionParameters.partByCount,
          periodSize, ongoingDuration)
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

  /** Perform a reduce operation on all the streams in a group set.
    */
  def reduceOperation[K, V] // format: OFF
      ( makeReduction: TypeTag[_] => Try[Reduction[V]],
        groupSet: StreamGroupSet[K, V, AsyncWithRange],
        optCount: Option[Int],
        optPeriod: Option[PeriodWithZone],
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
          stream.self.flexibleReduce(optPeriod, optCount, reduction, maxParts, ongoingDuration)
        }
      }

      reduced match {
        case Success(reducedStream) => reducedStream
        case Failure(err) => AsyncWithRange.error[K, Option[V]](err, stream.self.requestRange)
      }
    }
  }

}
// TODO remove temporary debugging code after the other reductions are implemented
//      val debugPrinted = reduced.mapStreams { stream =>
//        stream.doOnEach { pairs =>
//          pairs.foreachPair { (key, value) => println(s"SumTransform: $key:$value") }
//        }
//      }
