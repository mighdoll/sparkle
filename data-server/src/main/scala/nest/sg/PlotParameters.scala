package nest.sg
import scala.language.implicitConversions

import spray.json.DefaultJsonProtocol
import spray.json._

/** control sent from server to browser for server console configured charts */
case class PlotParameters
  ( sources: Array[PlotSource],
    title: Option[String] = None,
    units:Option[String] = None,
    timeSeries:Option[Boolean] = None,
    dashboard:Option[String] = None,
    defaultTransform:Option[String] = None,
    displayXAxis:Option[Boolean] = None,
    chartType:Option[String] = None )

object PlotParameters {
  /** automatically create plot parameters from a single string, interpreted as a columnPath */
  implicit def FromString(columnPath:String): PlotParameters = {
    val sources = PlotSource(columnPath, columnPath)
    PlotParameters(Array(sources))
  }
}

/** a particular data array and label to plot */
case class PlotSource(columnPath: String, label: String)

object PlotParametersJson extends DefaultJsonProtocol {
  implicit val PlotSourceFormat = jsonFormat2(PlotSource)
  implicit val PlotParametersFormat = jsonFormat8(PlotParameters.apply)
}

