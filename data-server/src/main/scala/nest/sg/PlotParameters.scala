package nest.sg
import scala.language.implicitConversions

import nest.sparkle.util.Opt

import spray.json.DefaultJsonProtocol
import spray.json._

/** control sent from server to browser for server console configured charts */
case class PlotParameters
  ( sources: Array[PlotSource],
    title: Opt[String] = None,
    units: Opt[String] = None,
    timeSeries: Opt[Boolean] = None,
    dashboard: Opt[String] = None,
    zoomTransform: Opt[String] = None,
    showXAxis: Opt[Boolean] = None,
    chartType: Opt[String] = None )

object PlotParameters {
  /** automatically create plot parameters from a single string, interpreted as a columnPath */
  implicit def FromString(columnPath:String): PlotParameters = {
    val sources = PlotSource(columnPath, columnPath)
    PlotParameters(Array(sources))
  }
}

/** a particular data array and label to plot */
case class PlotSource(columnPath: String, label: String)

object OptJson extends DefaultJsonProtocol {
  implicit def optFormat[T: JsonFormat] = new JsonFormat[Opt[T]] {
    def write(opt:Opt[T]): JsValue = optionFormat[T].write(opt.option)

    def read(value: JsValue): Opt[T] = {
      val option = optionFormat[T].read(value)
      Opt(option)
    }
  }
}

object PlotParametersJson extends DefaultJsonProtocol {
  import OptJson.optFormat
  implicit val PlotSourceFormat = jsonFormat2(PlotSource)
  implicit val PlotParametersFormat = jsonFormat8(PlotParameters.apply)
}

