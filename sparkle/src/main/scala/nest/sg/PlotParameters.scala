package nest.sg

import spray.json.DefaultJsonProtocol
import spray.json._

/** control sent from server to browser for server console configured charts */
case class PlotParameters(sources: Array[PlotSource], title: Option[String])

/** a particular data array and label to plot */
case class PlotSource(columnPath: String, label: String)

object PlotParametersJson extends DefaultJsonProtocol {
  implicit val PlotSourceFormat = jsonFormat2(PlotSource)
  implicit val PlotParametersFormat = jsonFormat2(PlotParameters)
}

