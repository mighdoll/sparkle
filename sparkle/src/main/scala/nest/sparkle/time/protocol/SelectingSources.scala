package nest.sparkle.time.protocol

import scala.concurrent.Future
import scala.collection.JavaConverters._
import scala.util.control.Exception._

import com.typesafe.config.Config
import spray.json._

import nest.sparkle.store.{ Column, Store }
import nest.sparkle.util.Instance
import nest.sparkle.time.protocol.RequestJson.CustomSelectorFormat

case class CustomSourceNotFound(msg: String) extends RuntimeException(msg)
case class MalformedSourceSelector(msg: String) extends RuntimeException(msg)

/** Support for managing custom and columnPath source selectors in protocol StreamRequest source
 *  messages. */
trait SelectingSources {
  def store: Store
  def rootConfig: Config
  private lazy val customSelectors: Map[String, CustomSourceSelector] = createCustomSelectors()

  /** Match an elements in the StreamRequest sources parameter to one of the registered 
   *  custom selectors. To match, the custom selectors must match by name (selector property
   *  in the StreamRequest). */
  object MatchCustom {
    
    /** extractor to apply to an element in the sources parameter */
    def unapply(jsObject: JsObject): Option[Seq[Future[Column[_, _]]]] = {
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
  def sourceColumns(sources: Seq[JsValue]): Seq[Future[Column[_, _]]] = {
    sources.flatMap { jsValue =>
      jsValue match {
        case JsString(columnPath) => Seq(store.column(columnPath))
        case MatchCustom(columns) => columns
        case x                    => Seq(Future.failed(MalformedSourceSelector(jsValue.toString)))
      }
    }
  }

  /** Instantiate the custom selectors listed in the .conf file. These will be addressable
   *  by name in future StreamRequest messages. */
  private def createCustomSelectors(): Map[String, CustomSourceSelector] = {
    lazy val sparkleApiConfig = rootConfig.getConfig("sparkle-time-server")
    sparkleApiConfig.getStringList("custom-selectors").asScala.map { className =>
      val selector: CustomSourceSelector = Instance.byName(className)(rootConfig, store)
      (selector.name, selector)
    }.toMap
  }
}
