package nest.sparkle.time.transform

import scala.concurrent.{ ExecutionContext, Future }
import spray.json.DefaultJsonProtocol._
import spray.json._
import nest.sparkle.time.protocol.JsonDataStream
import nest.sparkle.store.Column
import nest.sparkle.time.transform.StandardColumnTransform.executeTypedTransform

/** apply transforms (to help respond to StreamRequest messages) */
object Transform {

  /** apply requested StreamRequest transforms, returning OutputStreams that generate results on demand */
  def connectTransform( // format: OFF
        transform: String, transformParameters: JsObject,  
        futureColumns: Future[Seq[Column[_, _]]]
      ) (implicit execution: ExecutionContext): Future[Seq[JsonDataStream]] = { // format: ON

    val futureStreams = transform match {
      case SummaryTransform(columnTransform) =>
        executeTypedTransform(futureColumns, columnTransform, transformParameters)
      case DomainRangeTransform(columnTransform) =>
        executeTypedTransform(futureColumns, columnTransform, transformParameters)
      // LATER handle application-pluggable custom transforms
      case _ => ???
    }

    futureStreams
  }
  

}

