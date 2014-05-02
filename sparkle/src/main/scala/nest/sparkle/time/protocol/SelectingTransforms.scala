package nest.sparkle.time.protocol

import scala.concurrent.{ ExecutionContext, Future }
import scala.collection.JavaConverters._
import spray.json._
import nest.sparkle.store.Column
import nest.sparkle.time.transform.{ DomainRangeTransform, RawTransform }
import nest.sparkle.time.transform.{ SummaryTransform, TransformNotFound }
import nest.sparkle.time.transform.StandardColumnTransform.executeTypedTransform
import com.typesafe.config.Config
import nest.sparkle.time.transform.CustomTransform
import nest.sparkle.util.Instance
import nest.sparkle.util.Log

/** Identify the transform from a StreamRequest, including built in and custom
 *  transforms specified in the .conf file. */
trait SelectingTransforms extends Log {
  protected def rootConfig: Config
  private lazy val customTransforms: Map[String, CustomTransform] = createCustomTransforms()

  /** return the transform selected by the `transform` field in a StreamsRequest message
   *  or TransformNotFound */
  protected def selectTransform( // format: OFF
        transform: String, transformParameters: JsObject,
        futureColumns: Future[Seq[Column[_, _]]]
      ) (implicit execution: ExecutionContext): Future[Seq[JsonDataStream]] = { // format: ON

    /** match a custom transform by name */
    object MatchCustom {
      def unapply(transform:String):Option[CustomTransform] = {
        customTransforms.get(transform)
      }
    }
    
    val futureStreams = transform match {
      case SummaryTransform(columnTransform) =>
        executeTypedTransform(futureColumns, columnTransform, transformParameters)
      case DomainRangeTransform(columnTransform) =>
        executeTypedTransform(futureColumns, columnTransform, transformParameters)
      case RawTransform(columnTransform) =>
        executeTypedTransform(futureColumns, columnTransform, transformParameters)
      case MatchCustom(customTransform) =>
        executeTypedTransform(futureColumns, customTransform, transformParameters)        
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

}