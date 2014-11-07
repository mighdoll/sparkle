package nest.sparkle.time.protocol

import scala.concurrent.{ ExecutionContext, Future }
import scala.collection.JavaConverters._
import spray.json._
import nest.sparkle.store.Column
import nest.sparkle.time.transform.{ DomainRangeTransform, RawTransform }
import nest.sparkle.time.transform.SummaryTransform
import nest.sparkle.time.transform.StandardColumnTransform.{ runTransform, runColumnGroupsTransform, runMultiColumnTransform }
import com.typesafe.config.Config
import nest.sparkle.time.transform.CustomTransform
import nest.sparkle.util.{ Instance, ConfigUtil }
import nest.sparkle.util.Log
import nest.sparkle.time.transform.StandardSummaryTransform
import nest.sparkle.time.transform.StandardObjectTransform
import nest.sparkle.time.transform.StandardIntervalTransform
import nest.sparkle.time.transform.OnOffTransform
import nest.sparkle.time.transform.ColumnGroup
import nest.sparkle.measure.TraceId
import nest.sparkle.measure.Measurements

case class TransformNotFound(msg: String) extends RuntimeException(msg)

/** Identify the transform from a StreamRequest, including built in and custom
  * transforms specified in the .conf file.
  */
trait SelectingTransforms extends Log {
  protected def rootConfig: Config
  protected implicit def measurements:Measurements
  private lazy val customTransforms: Map[String, CustomTransform] = createCustomTransforms()

  private lazy val standardInterval = StandardIntervalTransform(rootConfig)
  private lazy val onOffInterval = OnOffTransform(rootConfig)

  /** return the results of the transform selected by the `transform` field in a StreamsRequest message
    * or TransformNotFound
    */
  protected def selectAndRunTransform( // format: OFF
        transform: String, 
        transformParameters: JsObject,
        futureColumnGroups: Future[Seq[ColumnGroup]]
      ) (implicit execution: ExecutionContext, traceId:TraceId)
      : Future[Seq[JsonDataStream]] = { // format: ON

    // TODO move transform processing to FutureColumnGroup style
    val allFutureColumns: Future[Seq[Column[_, _]]] = {
      futureColumnGroups.map { groups => groups.flatMap(_.columns)}
    }
    
    val futureStreams = transform match {
      case StandardSummaryTransform(columnTransform) =>
        runTransform(allFutureColumns, columnTransform, transformParameters)
      case standardInterval(columnTransform) =>
        runMultiColumnTransform(allFutureColumns, columnTransform, transformParameters)
      case onOffInterval(columnTransform) =>
        runColumnGroupsTransform(futureColumnGroups, columnTransform, transformParameters)
      case StandardObjectTransform(columnTransform) =>
        runTransform(allFutureColumns, columnTransform, transformParameters)
      case RawTransform(columnTransform) =>
        runTransform(allFutureColumns, columnTransform, transformParameters)
      case MatchCustomName(customTransform) =>
        runTransform(allFutureColumns, customTransform, transformParameters)
      case _ => Future.failed(TransformNotFound(transform))
    }

    futureStreams
  }

  /** Instantiate the custom transforms listed in the .conf file. These will be addressable
    * by name in StreamRequest messages.
    */
  private def createCustomTransforms(): Map[String, CustomTransform] = {
    val sparkleApiConfig = ConfigUtil.configForSparkle(rootConfig)
    sparkleApiConfig.getStringList("custom-transforms").asScala.map { className =>
      val transform: CustomTransform = Instance.byName(className)(rootConfig)
      (transform.name, transform)
    }.toMap
  }

  /** match a custom transform by name */
  private object MatchCustomName {
    def unapply(transform: String): Option[CustomTransform] = {
      customTransforms.get(transform)
    }
  }

}