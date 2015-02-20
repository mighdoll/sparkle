package nest.sg

import scala.reflect.runtime.universe.typeTag
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import nest.sparkle.store.{WriteableStore, Event}
import nest.sparkle.store.cassandra.RecoverCanSerialize
import nest.sparkle.store.cassandra.serializers._
import scala.reflect.runtime.universe._
import spray.json.JsObject
import scala.concurrent.{ExecutionContext, Future}
import nest.sparkle.time.server.SparkleAPIServer
import nest.sparkle.util.OptionConversion.OptionFuture
import nest.sparkle.util.ObservableFuture._
import nest.sparkle.util.Log
import spray.json.DefaultJsonProtocol
import nest.sparkle.util.RandomUtil
import nest.sparkle.util.Opt
import spray.json._
import PlotParametersJson._
import nest.sparkle.store.cassandra.CanSerialize
import rx.lang.scala.Observable

case class PlotParameterError(msg: String) extends RuntimeException

/** Supporting launching a graph from the repl.
  */
trait PlotConsole extends Log {
  def server: SparkleAPIServer
  def writeStore = server.readWriteStore
  implicit lazy val dispatcher = server.system.dispatcher
  val sessionId = RandomUtil.randomAlphaNum(5)
  var launched = false

  def plot[T: TypeTag](iterable: Iterable[T], name: String = nowString(), dashboard: String = "",
    title: Opt[String] = None, units: Opt[String] = None) {

    val events = iterable.zipWithIndex.map { case (value, index) => Event(index.toLong, value) }
    plotEvents(events, name, dashboard, title, units, false)
  }

  def plotEvents[T: TypeTag, U: TypeTag]
      ( events: Iterable[Event[T, U]], name: String = nowString(), dashboard: String = "",
        title: Opt[String] = None, units: Opt[String] = None, timeSeries: Boolean = true) {
    val stored =
      for {
        a <- writeEvents(events, name)
        b <- triggerBrowser(name, title, units, timeSeries)
      } yield ()

    stored.failed.foreach{ failure =>
      log.error("unable to store data for plotting", failure)
    }
  }

  var printedUrl = false  // true if we've already shown the url in this session

  /** show the browser url, so that the user can copy and paste from the console */
  def printDashboardUrl(): Unit = {
    if (!printedUrl) {
      val webPort = server.webPort
      println(s"http://localhost:$webPort/$sessionParameter")
      printedUrl = true
    }
  }


  def plotColumn(plotParameters: PlotParameters, dashboard: String = ""): Future[Unit] = {
    triggerBrowserParameters(plotParameters)
  }

  def plotStream[T: TypeTag]
      ( observable: Observable[T], name: String = nowString(), dashboard: String = "",
        title: Opt[String] = None, units: Opt[String] = None) {

    val events = observable.timestamp.map { case (time, value) => Event(time, value) }
    plotEventStream(events, name, dashboard, title, units, false)
  }

  private def sessionParameter = s"?sessionId=$sessionId"

  def launchBrowser(dashboard: String) {
    if (!launched) {
      launched = true
      server.launchDesktopBrowser(dashboard + sessionParameter)
    }
  }

  def plotEventStream[T: TypeTag, U: TypeTag]
      ( events: Observable[Event[T, U]], name: String = nowString(), dashboard: String = "",
        title: Opt[String] = None, units: Opt[String] = None, timeSeries: Boolean = true) {
    val storing = writeEventStream(events, name)
    for {
      _ <- storing.head.toFutureSeq
      _ <- triggerBrowser(name, title, units, timeSeries)
    } {
      storing.subscribe() // pull the remainder of the events into storage
    }
  }

  def writeEventStream[T: TypeTag, U: TypeTag](events: Observable[Event[T, U]], name: String = nowString()): Observable[Unit] = {
    val optSerializers =
      for {
        serializeKey <- RecoverCanSerialize.optCanSerialize[T](typeTag[T])
        serializeValue <- RecoverCanSerialize.optCanSerialize[U](typeTag[U])
      } yield {
        (serializeKey, serializeValue)
      }

    val obsSerializers = Observable.from(optSerializers)
    obsSerializers.flatMap {
      case (serializeKey, serializeValue) =>
        val columnPath = nameToPath(name)
        val futureColumn = writeStore.writeableColumn[T, U](columnPath)(serializeKey, serializeValue).toObservable
        val columnWritten: Observable[Unit] =
          for {
            column <- futureColumn
            event <- events
            written <- column.write(List(event)).toObservable
          } yield {
            log.trace(s"wrote $event to column: $columnPath")
            written
          }
        columnWritten
    }
  }

  def writeEvents[T: TypeTag, U: TypeTag](events: Iterable[Event[T, U]], name: String = nowString()): Future[Unit] = {
    val optSerializers =
      for {
        serializeKey <- RecoverCanSerialize.optCanSerialize[T](typeTag[T])
        serializeValue <- RecoverCanSerialize.optCanSerialize[U](typeTag[U])
      } yield {
        (serializeKey, serializeValue)
      }

    val futureSerializers = optSerializers.toFutureOr(PlotParameterError("can't serialize types: ${typeTag[T]} ${typeTag[U]} "))
    futureSerializers.flatMap {
      case (serializeKey, serializeValue) =>
        val columnPath = nameToPath(name)
        val futureColumn = writeStore.writeableColumn[T, U](columnPath)(serializeKey, serializeValue)
        val columnWritten: Future[Unit] =
          for {
            column <- futureColumn
            written <- column.write(events)
          } yield {
            log.debug(s"wrote ${events.size} items to column: $columnPath")
            written
          }
        columnWritten
    }
  }

  def write[T: TypeTag](iterable: Iterable[T], name: String = nowString()): Future[Unit] = {
    val events = iterable.zipWithIndex.map { case (value, index) => Event(index.toLong, value) }
    writeEvents[Long, T](events, name)
  }

  private def nameToPath(name: String): String = s"plot/$sessionId/$name"
  private val plotParametersPath = "_plotParameters"
  private val parametersColumnPath = s"plot/$sessionId/$plotParametersPath"

  /** Write plotParameters to a well known column. The browser watches this column and
    * updates its display on demand when it changes. */
  private def triggerBrowser(name: String, title: Option[String], unitsLabel: Option[String],
      timeSeries: Boolean): Future[Unit] = {
    val source = PlotSource(nameToPath(name), name)
    val timeSeriesOpt = if (timeSeries) Some(true) else None
    val plotParameters = PlotParameters(Array(source), title, unitsLabel, timeSeriesOpt)
    triggerBrowserParameters(plotParameters)
  }

  private def triggerBrowserParameters(plotParameters: PlotParameters): Future[Unit] = {
    printDashboardUrl()

    val plotParametersJson: JsValue = plotParameters.toJson
    val futureColumn = writeStore.writeableColumn[Long, JsValue](parametersColumnPath)
    val entry = Event(System.currentTimeMillis, plotParametersJson)

    log.trace(s"triggerBrowser: $plotParametersJson")
    futureColumn.flatMap { column =>
      column.write(List(entry))
    }
  }

  private val plotDateFormat = DateTimeFormat.forPattern("HH:mm:ss")
  private def nowString(): String = {
    import com.github.nscala_time._
    DateTime.now.toString(plotDateFormat)
  }

  // TODO cleanup old sessions, explicitly with a close(), or scanning by creation date
}

