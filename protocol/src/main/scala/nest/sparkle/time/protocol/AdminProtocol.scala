package nest.sparkle.time.protocol
import spray.json._

/** messages to the admin page */
case class ExportData(folder:String)

/** json version of admin page messages */
object AdminProtocol extends DefaultJsonProtocol {
  implicit val ExportDataFormat = jsonFormat1(ExportData)
}