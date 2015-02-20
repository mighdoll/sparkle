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

/** Interim work on supporting the launching of graph from the repl..
  */
trait PlotConsole extends Log {
  def server:SparkleAPIServer
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
        a <- storeEvents(events, name)
        b <- storeParameters(name, title, units, timeSeries)
      } yield {
        printDashboardUrl(dashboard)
      }

    stored.failed.foreach{ failure =>
      log.error("unable to store data for plotting", failure)
    }
  }

  def printDashboardUrl(dashboard:String): Unit = {
    val webPort = server.webPort
    println(s"http://localhost:$webPort/$sessionParameter")
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
    val storing = storeEventStream(events, name)
    for {
      _ <- storing.head.toFutureSeq
      _ <- storeParameters(name, title, units, timeSeries)
    } {
      storing.subscribe() // pull the remainder of the events into storage
      printDashboardUrl(dashboard)
    }
  }

  def storeEventStream[T: TypeTag, U: TypeTag](events: Observable[Event[T, U]], name: String = nowString()): Observable[Unit] = {
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

  def storeEvents[T: TypeTag, U: TypeTag](events: Iterable[Event[T, U]], name: String = nowString()): Future[Unit] = {
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

  def store[T: TypeTag](iterable: Iterable[T], name: String = nowString()): Future[Unit] = {
    val events = iterable.zipWithIndex.map { case (value, index) => Event(index.toLong, value) }
    storeEvents[Long, T](events, name)
  }

  private def nameToPath(name: String): String = s"plot/$sessionId/$name"
  private val plotParametersPath = "_plotParameters"

  private def storeParameters(name: String, title: Option[String], unitsLabel: Option[String],
    timeSeries: Boolean): Future[Unit] = {
    val parametersColumnPath = s"plot/$sessionId/$plotParametersPath"
    val source = PlotSource(nameToPath(name), name)
    val timeSeriesOpt = if (timeSeries) Some(true) else None
    val plotParameters = PlotParameters(Array(source), title, unitsLabel, timeSeriesOpt)
    val plotParametersJson: JsValue = plotParameters.toJson
    val futureColumn = writeStore.writeableColumn[Long, JsValue](parametersColumnPath)
    val entry = Event(System.currentTimeMillis, plotParametersJson)

    log.trace(s"storeParameters: $plotParametersJson")
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

