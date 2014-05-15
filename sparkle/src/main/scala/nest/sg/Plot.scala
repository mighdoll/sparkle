package nest.sg

import scala.reflect.runtime.universe.typeTag
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import nest.sparkle.store.Event
import nest.sparkle.store.cassandra.RecoverCanSerialize
import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.time.server.ServerLaunch
import scala.reflect.runtime.universe._
import spray.json.JsObject
import scala.concurrent.Future
import nest.sparkle.util.OptionConversion.OptionFuture
import nest.sparkle.util.Log
import spray.json.DefaultJsonProtocol
import nest.sparkle.util.RandomUtil
import nest.sparkle.util.Opt
import spray.json._
import PlotParametersJson._

case class PlotParameterError(msg: String) extends RuntimeException

/** Interim work on supporting the launching of graph from the repl..
  * TODO finish this.
  */
object Plot extends Log {
  val sessionId = RandomUtil.randomAlphaNum(5)
  
  private def configOverrides: List[(String, Any)] = List(
    "sparkle-time-server.port" -> 2323,
    "sparkle-time-server.web-root.resource" -> List("web/sg/plot-default"),
    "sparkle-time-server.sparkle-store-cassandra.key-space" -> "plot"
  )

  private lazy val server = ServerLaunch(None, configOverrides: _*)
  private lazy val store = server.writeableStore
  import server.system.dispatcher

  def plot[T: TypeTag](iterable: Iterable[T], name: String = nowString(), dashboard: String = "",
                       title: Opt[String] = None) {
    val stored =
      for {
        a <- store(iterable, name)
        b <- storeParameters(name, title)
      } yield {
        server.launchDesktopBrowser(dashboard + s"?sessionId=$sessionId")
      }

    stored.failed.foreach{ failure =>
      log.error("unable to store data for plotting", failure)
    }
  }

  private def nameToPath(name: String): String = s"plot/$sessionId/$name"
  private val plotParametersPath = "_plotParameters"

  private def storeParameters(name: String, title: Option[String]): Future[Unit] = {
    val parametersColumnPath = s"plot/$sessionId/$plotParametersPath"
    val source = PlotSource(nameToPath(name), name)
    val plotParameters = PlotParameters(Array(source), title)
    val plotParametersJson: String = plotParameters.toJson.prettyPrint
    val futureColumn = store.writeableColumn[Long, String](parametersColumnPath)
    val entry = Event(System.currentTimeMillis, plotParametersJson)

    log.trace(s"storeParameters: $plotParametersJson")
    futureColumn.flatMap { column =>
      column.write(List(entry))
    }
  }

  def store[T: TypeTag](iterable: Iterable[T], name: String = nowString()): Future[Unit] = {
    val serialOpt = RecoverCanSerialize.optCanSerialize[T](typeTag[T])
    val serialFuture = serialOpt.toFutureOr(PlotParameterError("can't serialize type: ${typeTag[T]}"))
    serialFuture.flatMap { serialize =>
      val columnPath = nameToPath(name)
      val futureColumn = store.writeableColumn[Long, T](columnPath)(LongSerializer, serialize)
      val columnWritten: Future[Unit] =
        for {
          column <- futureColumn
          events = iterable.zipWithIndex.map { case (value, index) => Event(index.toLong, value) }
          written <- column.write(events)
        } yield {
          written
        }

      columnWritten
    }
  }

  private val plotDateFormat = DateTimeFormat.forPattern("HH:mm:ss")
  private def nowString(): String = {
    import com.github.nscala_time._
    DateTime.now.toString(plotDateFormat)
  }

  // TODO cleanup old sessions, explicitly with a close(), or scanning by creation date
}

