package nest.sg

import com.typesafe.config.Config

import scala.reflect.runtime.universe.typeTag
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import nest.sparkle.datastream.DataArray
import nest.sparkle.store.Event
import nest.sparkle.store.cassandra.RecoverCanSerialize
import nest.sparkle.store.cassandra.serializers._
import scala.reflect.runtime.universe._
import spray.json.JsObject
import scala.concurrent.{ExecutionContext, Future}
import nest.sparkle.time.server.SparkleAPIServer
import nest.sparkle.util.OptionConversion.OptionFuture
import nest.sparkle.util.ObservableFuture._
import nest.sparkle.util.{ConfigUtil, ReflectionUtil, Log, RandomUtil, Opt}
import spray.json.DefaultJsonProtocol
import spray.json._
import PlotParametersJson._
import rx.lang.scala.Observable

case class PlotParameterError(msg: String) extends RuntimeException

/** Supporting launching a graph from the repl.
  */
trait PlotConsole extends Log {
  def rootConfig: Config
  def server: SparkleAPIServer
  def writeStore = server.readWriteStore
  implicit lazy val dispatcher = server.system.dispatcher
  val sessionId = RandomUtil.randomAlphaNum(5)
  var launched = false

  lazy val recoverCanSerialize = new RecoverCanSerialize(ConfigUtil.configForSparkle(rootConfig))

  /** load a collection of values into the store and plot them. The keys for the values
    * will be the indices in the collection. */
  def plotCollection[V: TypeTag]
      ( iterable: Iterable[V], name: String = nowString(),
        plotParameters: Opt[PlotParameters] = None )
      : Future[Unit] = {

    val pairs = iterable.zipWithIndex.map { case (value, index) => index.toLong -> value }
    plotPairs(pairs, name, plotParameters)
  }

  /** load a collection pairs into the store and plot them. */
  def plotPairs[K:TypeTag, V:TypeTag]
      ( pairs:Iterable[(K,V)], name: String = nowString(),
        plotParameters: Opt[PlotParameters] = None )
      : Future[Unit] = {
    implicit val keyClassTag = ReflectionUtil.classTag[K](typeTag[K])
    implicit val valueClassTag = ReflectionUtil.classTag[V](typeTag[V])
    val dataArray = DataArray.fromPairs(pairs)(keyClassTag, valueClassTag)
    plotDataArray(dataArray, name, plotParameters)
  }

  /** load a DataArray into the store and plot it */
  def plotDataArray[K:TypeTag, V:TypeTag]
      ( dataArray: DataArray[K,V], name: String = nowString(),
        optParameters:Opt[PlotParameters] = None)
      : Future[Unit] = {

    val columnPath = nameToPath(name)
    val plotParameters = {
      optParameters.option match {
        case Some(params) => params.withSources(Seq(PlotSource(columnPath, name)))
        case None         => PlotParameters(columnPath)
      }
    }
    println(s"plotting column $columnPath")

    for {
      _ <- writeDataArray(dataArray, name)
      _ <- triggerBrowserParameters(plotParameters)
    } yield Unit
  }

  /** plot a column from the store */
  def plotColumn(plotParameters: PlotParameters): Future[Unit] = {
    triggerBrowserParameters(plotParameters)
  }


  var printedUrl = false  // true if we've already shown the url in this session

  /** show the browser url, so that the user can copy and paste from the console */
  def printUrl(forcePrint:Boolean = true): Unit = {
    if (!printedUrl || forcePrint) {
      val webPort = server.webPort
      println(s"http://localhost:$webPort/$sessionParameter")
      printedUrl = true
    }
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
        serializeKey <- recoverCanSerialize.optCanSerialize[T](typeTag[T])
        serializeValue <- recoverCanSerialize.optCanSerialize[U](typeTag[U])
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

  /** write a dataArray to the store, returning a Future that completes when writing is complete */
  def writeDataArray[K:TypeTag,V:TypeTag](dataArray:DataArray[K,V], name:String = nowString()): Future[Unit] = {
    val optSerializers =
      for {
        serializeKey <- recoverCanSerialize.optCanSerialize[K](typeTag[K])
        serializeValue <- recoverCanSerialize.optCanSerialize[V](typeTag[V])
      } yield {
        (serializeKey, serializeValue)
      }

    val futureSerializers = optSerializers.toFutureOr {
      PlotParameterError("can't serialize types: ${typeTag[T]} ${typeTag[U]} ")
    }

    futureSerializers.flatMap {
      case (serializeKey, serializeValue) =>
        val columnPath = nameToPath(name)
        val futureColumn = writeStore.writeableColumn[K,V](columnPath)(serializeKey, serializeValue)
        val columnWritten: Future[Unit] =
          for {
            column <- futureColumn
            written <- column.writeData(dataArray)
          } yield {
            log.debug(s"wrote ${dataArray.size} items to column: $columnPath")
            written
          }
        columnWritten
    }
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
    printUrl(forcePrint = false)

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

