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

case class PlotParameterError(msg: String) extends RuntimeException

/** Interim work on supporting the launching of graph from the repl..
 *  TODO finish this. */
object Plot extends Log {
  private def configOverrides: List[(String, Any)] = List(
    "sparkle-time-server.port" -> 2323,
    "sparkle-time-server.web-root.resource" -> List("web/plot-default"),
    "sparkle-time-server.sparkle-store-cassandra.key-space" -> "plot"
  )

  private var started = false
  private lazy val server = ServerLaunch(None, configOverrides: _*)
  import server.system.dispatcher

  def plot[T: TypeTag](iterable: Iterable[T], name: String = nowString()) {
    val stored = store(iterable, name).map { _ =>
      server.launchDesktopBrowser()
    }

    stored.failed.foreach{ failure =>
      log.error("unable to store data for plotting", failure)
    }
  }

  def store[T: TypeTag](iterable: Iterable[T], name: String = nowString()): Future[Unit] = {
    val serialOpt = RecoverCanSerialize.optCanSerialize[T](typeTag[T])
    val serialFuture = serialOpt.toFutureOr(PlotParameterError("can't serialize type: ${typeTag[T]}"))
    serialFuture.flatMap { serialize =>
      val store = server.writeableStore
      val futureColumn = store.writeableColumn[Long, T](s"plot/$name")(LongSerializer, serialize)
      val columnWritten: Future[Unit] =
        for {
          column <- futureColumn
          events = iterable.zipWithIndex.map { case (value, index) => Event(index.toLong, value) }
          written <- column.write(events)
        } yield {
          started = true
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
}

case class PlotDescription(dashboard: JsObject)