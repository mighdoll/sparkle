package nest.sparkle.time.transform

import nest.sparkle.measure.Measurements
import nest.sparkle.measure.TraceId
import nest.sparkle.util.Instrumented
import spray.json.JsObject
import nest.sparkle.measure.Span
import com.typesafe.config.Config
import nest.sparkle.util.Log
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import nest.sparkle.time.protocol.JsonDataStream
import nest.sparkle.util.ConfigUtil.configForSparkle
import spray.json.{ JsObject, JsonFormat }
import nest.sparkle.util.{ Period, PeriodWithZone, RecoverJsonFormat, RecoverNumeric, RecoverOrdering }
import scala.reflect.runtime.universe._
import nest.sparkle.time.protocol.{ IntervalParameters, JsonDataStream, JsonEventWriter, KeyValueType }
import spire.math.Numeric
import spire.implicits._
import nest.sparkle.time.transform.FetchStreams.fetchData
import scala.concurrent.duration._
import scala.language.higherKinds
import nest.sparkle.time.transform.DataStreamType._
import nest.sparkle.core.ArrayPair

/** convert boolean on off events to intervals from multiple columns, then sum for each requested period.
  * Returns a json stream of the results. The results are in tabular form, with one summed amount for
  * each group of columns specified in the request.
  */
class IntervalSum2(rootConfig: Config)(implicit measurements: Measurements) extends MultiTransform with Log with Instrumented {
  val maxParts = configForSparkle(rootConfig).getInt("transforms.max-parts")
  val onOffParameters = OnOffParameters(rootConfig)

  override def transform  // format: OFF
      (futureGroups:Future[Seq[ColumnGroup]], transformParameters: JsObject)
      (implicit execution: ExecutionContext, traceId:TraceId)
      : Future[Seq[JsonDataStream]] = { // format: ON

    val track = TrackObservable()
    val span = Span.prepareRoot("IntervalSum", traceId).start()

    onOffParameters.validate[Any](futureGroups, transformParameters).flatMap { params: ValidParameters[Any] =>
      import params._
      val result = ( // format: OFF
            transformData
              (futureGroups, intervalParameters, periodSize)
              (keyType, numeric, jsonFormat, ordering, execution, span)
          ) // format: ON

      val monitoredResult = // attach a reporter on the result
        result.map { streams =>
          val trackedStreams = streams.map { jsonStream =>
            jsonStream.copy(dataStream = track.finish(jsonStream.dataStream))
          }
          track.allFinished.foreach { _ => span.complete() }
          trackedStreams
        }
      monitoredResult
    }
  }

  /** An initial start at re-implementing IntervalSum on the ArrayPair substrate as a demonstration.
    * Compare to OnOffIntervalSum.transformData to see the remaining work.
    */
  private def transformData[K: TypeTag: Numeric: JsonFormat: Ordering] // format: OFF
      (futureGroups:Future[Seq[ColumnGroup]],
       intervalParameters:IntervalParameters[K],
       periodSize:Option[PeriodWithZone])
      (implicit execution: ExecutionContext, parentSpan:Span)
      : Future[Seq[JsonDataStream]] = { // format: ON

    for {
      data <- fetchData[K, Boolean](futureGroups, intervalParameters.ranges, Some(rangeExtender), Some(parentSpan))
      intervals = onOffToIntervals(data)
    } yield {

    }

    ???
  }

  /** start at implementing onOffToIntervals. Demonstrates that the typing information can flow through */
  def onOffToIntervals[K: Numeric: TypeTag, S[_, _]: DataStream] // format: OFF
      (streams:StreamGroupSet[K,Boolean,S])
      (implicit execution: ExecutionContext)
      : StreamGroupSet[K, K, S] = { // format: ON

    def toIntervals(source: ArrayPair[Long, Boolean]): ArrayPair[Long, Long] = {
      // note: This can't @specialize, because Function2 isn't specialized on Boolean
      //      source.mapElements{ (key, value) =>
      //        val length = if (value) 1L else 2L // placeholder for a real implementation
      //        (key, length)
      //      }

      val count = source.keys.length
      val newKeys = new Array[Long](count)
      val newValues = new Array[Long](count)
      var i = 0
      while (i < count) {
        val key = source.keys(i)
        val value = source.values(i)
        newKeys(i) = key
        newValues(i) = if (value) 1L else 2L // placeholder for a real implementation
        i += 1
      }
      ArrayPair(newKeys, newValues)
    }

    streams.mapData { arrayPair: ArrayPair[K, Boolean] =>
      typeOf[K] match {
        case typ if typ =:= typeOf[Long] =>
          val result: ArrayPair[Long, Long] = toIntervals(arrayPair.asInstanceOf[ArrayPair[Long, Boolean]])
          result.asInstanceOf[ArrayPair[K, K]]
        case _ => ??? // TODO handle generic case
      }
    }

  }

  private def rangeExtender[T: Numeric]: ExtendRange[T] = {
    val numericKey = implicitly[Numeric[T]]
    ExtendRange[T](before = Some(numericKey.fromLong(-1.day.toMillis))) // TODO make adjustable in the .conf file
  }

}