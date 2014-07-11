package nest.sparkle.time.protocol

import scala.concurrent.{ ExecutionContext, Future }
import scala.collection.JavaConverters._
import spray.json._
import nest.sparkle.store.Column
import nest.sparkle.time.transform.{ DomainRangeTransform, RawTransform }
import nest.sparkle.time.transform.{ SummaryTransform, TransformNotFound }
import nest.sparkle.time.transform.StandardColumnTransform.{runTransform, runMultiColumnTransform}
import com.typesafe.config.Config
import nest.sparkle.time.transform.CustomTransform
import nest.sparkle.util.Instance
import nest.sparkle.util.Log
import nest.sparkle.time.transform.StandardSummaryTransform
import nest.sparkle.time.transform.StandardObjectTransform
import nest.sparkle.time.transform.StandardIntervalTransform

/** Identify the transform from a StreamRequest, including built in and custom
  * transforms specified in the .conf file.
  */
trait SelectingTransforms extends Log {
  protected def rootConfig: Config
  private lazy val customTransforms: Map[String, CustomTransform] = createCustomTransforms()

  /** return the results of the transform selected by the `transform` field in a StreamsRequest message
    * or TransformNotFound
    */
  protected def selectTransform( // format: OFF
        transform: String, transformParameters: JsObject,
        futureColumns: Future[Seq[Column[_, _]]]
      ) (implicit execution: ExecutionContext): Future[Seq[JsonDataStream]] = { // format: ON

    val futureStreams = transform match {
      case StandardSummaryTransform(columnTransform) =>
        runTransform(futureColumns, columnTransform, transformParameters)
      case StandardIntervalTransform(columnTransform) =>
        runMultiColumnTransform(futureColumns, columnTransform, transformParameters)
      case StandardObjectTransform(columnTransform) =>
        runTransform(futureColumns, columnTransform, transformParameters)
      case RawTransform(columnTransform) =>
        runTransform(futureColumns, columnTransform, transformParameters)
      case MatchCustomName(customTransform) =>
        runTransform(futureColumns, customTransform, transformParameters)
      case _ => Future.failed(TransformNotFound(transform))
    }

    futureStreams
  }

  /** Instantiate the custom transforms listed in the .conf file. These will be addressable
    * by name in future StreamRequest messages.
    */
  private def createCustomTransforms(): Map[String, CustomTransform] = {
    lazy val sparkleApiConfig = rootConfig.getConfig("sparkle-time-server")
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