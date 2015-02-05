package nest.sparkle.time.transform

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.reflect.runtime.universe
import scala.util.{Failure, Success, Try}
import com.typesafe.config.Config
import spray.json.JsObject
import nest.sparkle.measure.{Measurements, Span, TraceId}
import nest.sparkle.time.protocol.{JsonDataStream, JsonEventWriter, KeyValueType}
import nest.sparkle.time.transform.FetchStreams.fetchData
import nest.sparkle.util.{Log, PeriodWithZone, RecoverNumeric}
import nest.sparkle.datastream.{AsyncWithRange, TwoPartStream, ReduceSum, StreamGroupSet}

/** support protocol "transform" field matching for the reduction transforms */ 
case class ReductionTransform(rootConfig: Config)(implicit measurements: Measurements) extends TransformMatcher {
  override type TransformType = MultiTransform
  override def prefix = "reduce"
  lazy val sumTransform = new SumTransform(rootConfig)

  override def suffixMatch = _ match {
    case "sum" => sumTransform
  }
}

/** TODO refactor into a generic transform, not just Sum */
case class SumTransform(rootConfig: Config)(implicit measurements: Measurements)
    extends MultiTransform with Log {

  private val validator = ValidateReductionParameters()

  override def transform  // format: OFF
      (futureGroups:Future[Seq[ColumnGroup]], transformParameters: JsObject)
      (implicit execution: ExecutionContext, traceId:TraceId)
      : Future[Seq[JsonDataStream]] = { // format: ON
    implicit val span = Span.prepareRoot("Sum", traceId).start()

    for {
      ValidReductionParameters(
        keyType, keyJsonFormat, keyOrdering, reductionParameters, periodSize
        ) <- validator.validate[Any](futureGroups, transformParameters)
      data <- fetchData[Any, Any](futureGroups, reductionParameters.ranges, None, Some(span))
      reduced = reduceSum(data, periodSize)
    } yield {
      
      // TODO remove temporary debugging code after the other reductions are implemented
//      val debugPrinted = reduced.mapStreams { stream =>
//        stream.doOnEach { pairs =>
//          pairs.foreachPair { (key, value) => println(s"SumTransform: $key:$value") }
//        }
//      }
      
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

  /** TODO refactor this into a generic reducer, not just sum */
  def reduceSum[K, V] // format: OFF
      ( groupSet: StreamGroupSet[K, V, AsyncWithRange], optPeriod:Option[PeriodWithZone] )
      ( implicit execution: ExecutionContext, parentSpan: Span )
      : StreamGroupSet[K, Option[V], AsyncWithRange] = { // format: ON

    groupSet.mapStreams { stream =>
      implicit val keyType = stream.keyType
      implicit val valueType = stream.valueType

      val reduced: Try[TwoPartStream[K, Option[V], AsyncWithRange]] = {
        for {
          numericValue <- RecoverNumeric.tryNumeric[V](valueType)
          sum = ReduceSum[V]()(numericValue)
        } yield {
          // TODO support reduceByCount too (supported by the existing reductions)
          stream.self.reduceByOptionalPeriod(optPeriod, sum, maxParts)
        }
      }
      
      reduced match {
        case Success(reducedStream) => reducedStream
        case Failure(err) => AsyncWithRange.error[K, Option[V]](err, stream.self.requestRange)
      }
    }
  }

}