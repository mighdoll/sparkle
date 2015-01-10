package nest.sparkle.time.server
  import spray.http._
import spray.httpx.marshalling._
import spray.http.MediaTypes.`text/tab-separated-values`

object TsvContent {
  implicit val TsvContentMarshaller = 
    Marshaller.of[TsvContent](`text/tab-separated-values`) { (value, contentType, ctx) =>
      ctx.marshalTo(HttpEntity(`text/tab-separated-values`, value.content))
    }
}

case class TsvContent(content:String)