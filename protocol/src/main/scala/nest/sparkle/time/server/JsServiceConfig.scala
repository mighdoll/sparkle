package nest.sparkle.time.server

import spray.json.DefaultJsonProtocol

/** served to the javascript client so it can find the web socket port */
case class JsServiceConfig(webSocketPort:Int) 

object JsServiceConfigJson extends DefaultJsonProtocol {
  implicit val JsServiceConfigFormat = jsonFormat1(JsServiceConfig)
}