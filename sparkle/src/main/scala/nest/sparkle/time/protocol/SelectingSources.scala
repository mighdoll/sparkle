package nest.sparkle.time.protocol

import scala.annotation.implicitNotFound
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.Exception.catching
import com.typesafe.config.Config
import spray.json._
import nest.sparkle.store.{ Column, Store }
import nest.sparkle.time.protocol.RequestJson.CustomSelectorFormat
import nest.sparkle.util.{ Instance, ConfigUtil }
import nest.sparkle.time.transform.ColumnGroup

case class CustomSourceNotFound(msg: String) extends RuntimeException(msg)
case class MalformedSourceSelector(msg: String) extends RuntimeException(msg)

/** Support for managing custom and columnPath source selectors in protocol StreamRequest source
  * messages.
  */
trait SelectingSources {
  def store: Store
  def rootConfig: Config
  private lazy val customSelectors: Map[String, CustomSourceSelector] = createCustomSelectors()

  /** Match an elements in the StreamRequest sources parameter to one of the registered
    * custom selectors. To match, the custom selectors must match by name (selector property
    * in the StreamRequest).
    */
  object MatchCustom {
    /** extractor to apply to an element in the sources parameter */
    def unapply(jsObject: JsObject)(implicit execution: ExecutionContext) // format: OFF
        : Option[Future[Seq[ColumnGroup]]] = { // format: ON
      val selectorOpt =
        catching(classOf[DeserializationException]) opt jsObject.convertTo[CustomSelector]

      for {
        selector <- selectorOpt
        customSelector <- customSelectors.get(selector.selector)
      } yield {
        customSelector.selectColumns(selector.selectorParameters)
      }
    }
  }

  /** Return the columns selected by the sources parameter in the StreamRequest */
  def sourceColumns(sources: Seq[JsValue], authorizer: Authorizer)(implicit execution: ExecutionContext) // format: OFF
      : Future[Seq[ColumnGroup]] = { // format: ON

    val seqFutures: Seq[Future[Seq[ColumnGroup]]] =
      sources.map { jsValue =>

        jsValue match {
          // TODO need to authorize the request
          case JsString(columnPath) =>
            val futureColumn = store.column(columnPath)
            futureColumn.map(column => Seq(ColumnGroup(Seq(column))))
          case MatchCustom(columns) => columns
          case _ =>
            Future.failed(MalformedSourceSelector(jsValue.toString))
          // TODO report a custom error when source selector not found
        }
      }
    Future.sequence(seqFutures).map { _.flatten }

  }

  /** Instantiate the custom selectors listed in the .conf file. These will be addressable
    * by name in future StreamRequest messages.
    */
  private def createCustomSelectors(): Map[String, CustomSourceSelector] = {
    val sparkleApiConfig = ConfigUtil.configForSparkle(rootConfig)
    sparkleApiConfig.getStringList("custom-selectors").asScala.map { className =>
      val selector: CustomSourceSelector = Instance.byName(className)(rootConfig, store)
      (selector.name, selector)
    }.toMap
  }
}
