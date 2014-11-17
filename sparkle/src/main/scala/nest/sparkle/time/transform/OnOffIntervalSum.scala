package nest.sparkle.time.transform

import com.github.nscala_time.time.Implicits.{ richReadableInstant, richReadableInterval }
import com.typesafe.config.Config
import java.util.TimeZone
import nest.sparkle.store.Event
import nest.sparkle.store.EventGroup.{ OptionRows, transposeSlices }
import nest.sparkle.time.protocol.{ IntervalParameters, JsonDataStream, JsonEventWriter, KeyValueType }
import nest.sparkle.time.transform.ItemStreamTypes._
import nest.sparkle.time.transform.FetchItems.fetchItems
import nest.sparkle.time.transform.PeriodPartitioner.timePartitionsFromRequest
import nest.sparkle.util.{ Period, PeriodWithZone, RecoverJsonFormat, RecoverNumeric, RecoverOrdering }
import nest.sparkle.util.ConfigUtil.configForSparkle
import nest.sparkle.util.KindCast.castKind
import nest.sparkle.util.Log
import nest.sparkle.util.OptionConversion.OptionFuture
import nest.sparkle.util.TryToFuture.FutureTry
import rx.lang.scala.Observable
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.reflect.runtime.universe._
import scala.util.{ Failure, Success, Try }
import scala.util.control.Exception.nonFatalCatch
import spire.math.Numeric
import spire.implicits._
import spray.json.{ JsObject, JsonFormat }
import spray.json.DefaultJsonProtocol._
import nest.sparkle.util.Instrumented
import nest.sparkle.measure.TraceId
import nest.sparkle.measure.Span
import nest.sparkle.measure.Measurements
import nest.sparkle.measure.UnstartedSpan
import scala.collection.mutable.ArrayBuffer
import nest.sparkle.measure.Span

/** support for protocol request parsing by matching the onOffIntervalSum transform by string name */
case class OnOffTransform(rootConfig: Config)(implicit measurements: Measurements) extends TransformMatcher {
  override type TransformType = MultiTransform
  override def prefix = "onoff"
  lazy val onOffIntervalSum = new OnOffIntervalSum(rootConfig)

  override def suffixMatch = _ match {
    case "intervalsum" => onOffIntervalSum
  }
}

/** convert boolean on off events to intervals from multiple columns, then sum for each requested period.
  * Returns a json stream of the results. The results are in tabular form, with one summed amount for
  * each group of columns specified in the request.
  */
class OnOffIntervalSum(rootConfig: Config)(implicit measurements: Measurements) extends MultiTransform with Log with Instrumented {
  val maxParts = configForSparkle(rootConfig).getInt("transforms.max-parts")
  val onOffParameters = OnOffParameters(rootConfig)

  override def transform  // format: OFF
      (futureGroups:Future[Seq[ColumnGroup]], transformParameters: JsObject)
      (implicit execution: ExecutionContext, traceId:TraceId)
      : Future[Seq[JsonDataStream]] = { // format: ON

    val track = TrackObservable()
    val span = Span.prepareRoot("IntervalSum", traceId, opsReport = true).start()

    onOffParameters.validate[Any](futureGroups, transformParameters).flatMap { params: ValidParameters[Any] =>
      import params._
      val result = ( // format: OFF
            transformData
              (futureGroups, intervalParameters, periodSize)
              (keyType, numeric, jsonFormat, ordering, execution, span)
          ) // format: ON

      val monitoredResult = // attach a reporter on the result
        result.map { streams =>
          val trackedStreams = streams.map { jsonStream =>
            jsonStream.copy(dataStream = track.finish(jsonStream.dataStream))
          }
          track.allFinished.foreach { _ => span.complete() }
          trackedStreams
        }
      monitoredResult
    }
  }

  /** Convert boolean on off events to intervals, then merge and sum the interval lengths.
    * Return a json stream of the results in tabular form, with one sum per each requested group of columns.
    */
  private def transformData[K: TypeTag: Numeric: JsonFormat: Ordering] // format: OFF
      (futureGroups:Future[Seq[ColumnGroup]],
       intervalParameters:IntervalParameters[K],
       periodSize:Option[PeriodWithZone])
      (implicit execution: ExecutionContext, parentSpan:Span)
      : Future[Seq[JsonDataStream]] = { // format: ON

    for {
      // fetch the data
      itemSet <- fetchItems[K](futureGroups, intervalParameters.ranges, Some(rangeExtender), Some(parentSpan))
      booleanValues = itemSet.castValues[Boolean] // TODO combine with fetchItems

      // transform on/off to a table reporting total overlap (one column per group, one row per period)
      intervalSet = onOffToIntervals(booleanValues, parentSpan)
      bufferedIntervals = BufferedIntervalSet.fromRangedSet(intervalSet, parentSpan)
      intervalPerGroup = mergeIntervals(bufferedIntervals, parentSpan)
      intervalsByPeriod = intersectPerPeriod(intervalPerGroup, periodSize, parentSpan)
      sums = sumIntervals(intervalsByPeriod, parentSpan)
      asTable = tabularGroups[K, K](sums, parentSpan)
      tableWithZeros = tabularBlanksToZeros(asTable, parentSpan)
      jsonStream = rowsToJson[K, K](tableWithZeros, parentSpan)
    } yield {
      Seq(jsonStream)
    }
  }

  /** Convert data values to json.
    *
    * Normally called on a single stream. (if more streams are provided, concatenates all the
    * streams in all the groups and stacks to a single stream.)
    */
  private def rowsToJson[K: JsonFormat, V: JsonFormat] // format: OFF
    (itemSet:BufferedMultiValueSet[K,V], parentSpan:Span)
    (implicit execution:ExecutionContext)
    : JsonDataStream = { // format: ON

    val futureStreams = // flatten groups and stacks to get a collection of streams
      for {
        group <- itemSet.groups
        stack <- group.stacks
        futureStackItems = stack.streams.map(_.initialEntire)
      } yield {
        val stackCombinedItems = Future.sequence(futureStackItems).map { streams => streams.reduce(_ ++ _) }
        stackCombinedItems
      }

    // merge all streams into one json stream. (TODO probably this routine should take a single stream, not an ItemSet container)
    val mergedFuture = Future.sequence(futureStreams).map(_.flatten)
    val merged: Observable[Seq[MultiValue[K, V]]] = Observable.from(mergedFuture)

    JsonDataStream(
      dataStream = JsonEventWriter.fromObservableSeq(merged, Some(parentSpan)),
      streamType = KeyValueType
    )
  }

  /** return an optional Period wrapped in a Try, by parsing the client-protocol supplied
    * IntervalParameters.
    */ // TODO move to somewhere generic
  private def periodParameter[T](optPeriod: Option[String]): Try[Option[Period]] = {
    optPeriod match {
      case Some(periodString) =>
        val tryResult = Period.parse(periodString).toTryOr(InvalidPeriod(s"periodString"))
        tryResult.map { Some(_) } // wrap successful result in an option (we parsed it and there was a period string)
      case None =>
        Success(None) // we parsed successfully: there was no period
    }
  }

  def rangeExtender[T: Numeric]: ExtendRange[T] = {
    val numericKey = implicitly[Numeric[T]]
    ExtendRange[T](before = Some(numericKey.fromLong(-1.day.toMillis))) // TODO make adjustable in the .conf file
  }

  def onOffToIntervals[K: TypeTag: Numeric] // format: OFF
      (onOffSet: RawRangedSet[K, Boolean], parentSpan:Span) 
      (implicit execution: ExecutionContext)
      : RangedIntervalSet[K] = { // format: ON
    val groups = onOffSet.groups.map { group =>
      val groups = group.stacks.map { stack =>
        val streams = stack.streams.map { stream =>
          val initial = oneStreamToIntervals(stream.initial, parentSpan)
          // ongoing is disabled for now, so we can expose performance metrics on the entire stream
          //          val ongoing = oneStreamToIntervals(stream.ongoing, parentSpan)
          val ongoing = Observable.empty
          new RangedIntervalStream(initial, ongoing, stream.fromRange)
        }
        new RangedIntervalStack(streams)
      }
      new RangedIntervalGroup(groups, group.name)
    }
    new RangedIntervalSet(groups)
  }

  /** convert on/off events to intervals */
  private def oneStreamToIntervals[T: Numeric] // format: OFF
      (onOffs: Observable[Event[T, Boolean]], parentSpan:Span)
      : Observable[IntervalItem[T]] = { // format: ON
    val numeric = implicitly[Numeric[T]]

    // As we walk through the on/off data, produce this state record. 
    // We save the intermediate state so that we can:
    // 1) accumulate the length of 'on' periods, even across sequences like: off,on,on,on,off
    // 2) emit a final record in case the final on period hasn't yet closed
    // 3) LATER - optionally add 'off' signals in case of data gaps
    case class IntervalState(current: Option[IntervalItem[T]], emit: Option[IntervalItem[T]]) {
      require(current.isEmpty || emit.isEmpty)
    }


    val intervalStates =
      onOffs.toVector.flatMap { seqOnOffs =>  // TODO re-enable incemental processing, rather than forcing everthing to a Seq
        Span.prepare("onOffToIntervals", parentSpan).time {
          val intervalStates =
            seqOnOffs.scanLeft(IntervalState(None, None)) { (state, event) =>
              (state.current, event.value) match {
                case (None, true) => // start a new zero length interval
                  val current = IntervalItem(event.argument, numeric.zero)
                  IntervalState(Some(current), None)
                case (None, false) => // continue with no interval
                  state
                case (Some(item), true) => // accumulate into current interval, making it a bit longer
                  val start = item.start
                  val size = event.argument - start
                  val current = IntervalItem(start, size)
                  IntervalState(Some(current), None)
                case (Some(item), false) => // end started interval 
                  val start = item.start
                  val size = event.argument - start
                  val emit = IntervalItem(start, size)
                  IntervalState(None, Some(emit))
              }
            }
          Observable.from(intervalStates)
        }
      }

    // we need to cache to preserve the last interval
    val cachedIntervals = intervalStates.cache
    val mainIntervals = cachedIntervals.flatMap { state =>
      state match { // emit the completed intervals as events
        case IntervalState(_, Some(emit)) => Observable.from(List(emit))
        case _                            => Observable.empty
      }
    }

    val lastInterval = cachedIntervals.last.flatMap { state =>
      state match {
        // close and emit a final started-but-not-yet-completed event. 
        case IntervalState(Some(item), _) => Observable.from(List(item))
        case _                            => Observable.empty
      }
    }

    val intervals = mainIntervals ++ lastInterval
    intervals
  }

  /** merge intervals into one stream per group */
  def mergeIntervals[K: TypeTag: Numeric: Ordering] // format: OFF
      (set: BufferedIntervalSet[K], parentSpan:Span)
      (implicit execution: ExecutionContext) 
      : BufferedIntervalSet[K] = { // format: ON

    val groups = set.groups.map { group =>
      val eachStackCombined: Seq[Future[Seq[IntervalItem[K]]]] =
        group.stacks.map { stack =>
          val stackTogether: Future[Seq[IntervalItem[K]]] = {
            val initialFutures: Seq[Future[Seq[IntervalItem[K]]]] = stack.streams.map(_.initialEntire)
            val initialFuture = Future.sequence(initialFutures)
            initialFuture.map(_.flatten)
          }
          stackTogether
        }
      val allCombined = Future.sequence(eachStackCombined).map(_.flatten)
      val allMerged = allCombined.map { items =>
        Span.prepare("mergeIntervals", parentSpan).time {
          val sorted = items.sortBy(_.argument)
          IntervalItem.combine(sorted)
        }
      }

      // LATER merge ongoing intervals as well (probably requires buffering to find overlaps..)
      val ongoing = Observable.empty

      // For now we just take the first range provided. TODO intelligently merge request ranges
      val mergedRange =
        for {
          stack <- group.stacks.headOption
          stream <- stack.streams.headOption
          fromRange <- stream.fromRange
        } yield {
          fromRange
        }

      val oneStream = new BufferedIntervalStream(allMerged, ongoing, mergedRange)
      val stack = new BufferedIntervalStack(Seq(oneStream))
      new BufferedIntervalGroup(Seq(stack), group.name)
    }
    new BufferedIntervalSet(groups)
  }

  /** Interprets the source events as intervals, and returns for each partition a single interval
    * representing the total overlap of the source intervals with the period partition.
    */
  private def intersectPerPeriod[K: TypeTag: Numeric] // format: OFF
      (intervalSet: BufferedIntervalSet[K],
       optPeriod: Option[PeriodWithZone],
       parentSpan: Span)
      (implicit execution: ExecutionContext): PeriodIntervalSet[K] = { // format: ON
    optPeriod match {
      case Some(periodWithZone) =>
        val groups = intervalSet.groups.map { group =>
          val stacks = group.stacks.map { stack =>
            val streams = stack.streams.map { stream =>
              val futurePartsAndIntersected = stream.initialEntire.map { items =>
                Span.prepare("intersectPerPeriod", parentSpan).time {
                  val timeParts = timePartitionsFromRequest(items, stream.fromRange, periodWithZone)
                  val intersected: Seq[IntervalItem[K]] = timeParts.partIterator().toSeq.flatMap { jodaInterval =>
                    IntervalItem.jodaMillisIntersections(items, jodaInterval)
                  }
                  (timeParts, intersected)
                }
              }
              val futureIntersected = futurePartsAndIntersected.map { case (_, intersected) => intersected }
              val futureTimeParts = futurePartsAndIntersected.map { case (timeParts, _) => timeParts }
              new PeriodIntervalStream[K](futureIntersected, Observable.empty, stream.fromRange, Some(futureTimeParts))
            }
            new PeriodIntervalStack(streams)
          }
          new PeriodIntervalGroup(stacks, group.name)
        }
        new PeriodIntervalSet(groups)
      case None =>
        val groups = intervalSet.groups.map { group =>
          val stacks = group.stacks.map { stack =>
            val streams = stack.streams.map { stream =>
              new PeriodIntervalStream[K](stream.initialEntire, Observable.empty, stream.fromRange, None)
            }
            new PeriodIntervalStack(streams)
          }
          new PeriodIntervalGroup(stacks, group.name)
        }
        new PeriodIntervalSet(groups)
    }
  }

  /** add up the length of each interval in each requested period, or in the entire data set if no period is specified */
  private def sumIntervals[K: TypeTag: Numeric] // format: OFF
      (intervalSet: PeriodIntervalSet[K], parentSpan:Span)
      (implicit execution:ExecutionContext)
      : BufferedRawItemSet[K,K] = { // format: ON
    val groups = intervalSet.groups.map { group =>
      val stacks = group.stacks.map { stack =>
        val streams = stack.streams.map { stream =>
          stream.timeParts match {
            case Some(times) => sumPerPeriod(stream, times, parentSpan)
            case None        => sumEntire(stream, parentSpan)
          }
        }
        new BufferedRawItemStack(streams)
      }
      new BufferedRawItemGroup(stacks, group.name)
    }
    new BufferedRawItemSet(groups)
  }

  /** add up the length of each interval in each requested period */
  private def sumPerPeriod[K: TypeTag: Numeric, V: TypeTag: Numeric, I <: Event[K, V]] // format: OFF
      (stream:BufferedItemStream[K,V,I], futureParts:Future[TimePartitions], parentSpan:Span)
      (implicit execution:ExecutionContext)
      : BufferedItemStream[K,V,Event[K,V]] = { // format: ON

    val futureItems =
      for {
        parts <- futureParts
        items <- stream.initialEntire
      } yield {
        Span.prepare("sumPerPeriod", parentSpan).time {
          // TODO remove stuff from active
          // TODO don't check stuff in active that starts after the end of this period
          val active = items

          val zeroValue = implicitly[Numeric[V]].zero
          val summaryItems =
            parts.partIterator().take(maxParts).map { jodaInterval =>
              val start: K = Numeric[Long].toType[K](jodaInterval.start.millis)
              val end: K = Numeric[Long].toType[K](jodaInterval.end.millis)
              val inPeriod = active.filter { item => item.argument >= start && item.argument < end }

              val sum = inPeriod.map(_.value).reduceOption(_ + _).getOrElse(zeroValue)
              Event(start, sum)
            }
          summaryItems.toVector
        }
      }
    new BufferedItemStream[K, V, Event[K, V]](futureItems, stream.ongoing, stream.fromRange)
  }

  /** add up the length of each interval in the data set (no partitioning by time period) */
  private def sumEntire[K: TypeTag, V: TypeTag: Numeric, I <: Event[K, V]] // format: OFF
      (stream:BufferedItemStream[K,V,I], parentSpan:Span)
      (implicit execution:ExecutionContext)
      :BufferedItemStream[K,V,Event[K,V]] = { // format: ON
    val initial =
      stream.initialEntire.map { items =>
        Span.prepare("sumEntire", parentSpan).time {
          items.headOption.map(_.argument) match {
            case Some(start) =>
              val total = items.map(_.value).reduce(_ + _)
              val item = Event(start, total)
              Seq(item)
            case None =>
              Seq()
          }
        }
      }
    new BufferedItemStream[K, V, Event[K, V]](initial, stream.ongoing, stream.fromRange)
  }

  /** combine multiple groups with multiple streams containing individual values into a single stream containg tabular values */
  private def tabularGroups[K: TypeTag, V: TypeTag] // format: OFF
      (itemSet: BufferedRawItemSet[K, V], parentSpan:Span)
      (implicit execution: ExecutionContext)
      : BufferedOptionRowSet[K, V] = { // format: ON

    val futureSeqPerGroup: Seq[Future[Seq[Event[K, V]]]] = // flatten stacks to get a collection of item sequences
      for {
        group <- itemSet.groups
        stack <- group.stacks
        futureStackItems = stack.streams.map(_.initialEntire)
      } yield {
        val stackCombinedItems = Future.sequence(futureStackItems).map { items =>
          items.reduce(_ ++ _)
        }
        stackCombinedItems
      }

    // composite each group into rows with multiple values
    val grouped: Future[OptionRows[K, V]] = Future.sequence(futureSeqPerGroup).map { seqPerGroup =>
      Span.prepare("tabularGroups", parentSpan).time {
        val rows = transposeSlices(seqPerGroup)
        rows.map { row =>
          val key = row.head.get.asInstanceOf[K]
          val values = row.tail.asInstanceOf[Seq[Option[V]]]
          Event(key, values)
        }
      }
    }

    // return a set wwith the composited array of option-values rows as values
    val stream = new BufferedOptionRowStream(grouped, Observable.empty, None)
    val stack = new BufferedOptionRowStack(Seq(stream))
    val group = new BufferedOptionRowGroup(Seq(stack), None)
    new BufferedOptionRowSet(Seq(group))
  }

  /** convert table rows containing Option values into zeros */
  private def tabularBlanksToZeros[K: TypeTag, V: TypeTag: Numeric] // format: OFF
      (itemSet:BufferedOptionRowSet[K,V], parentSpan:Span)
      (implicit execution: ExecutionContext)
      : BufferedMultiValueSet[K, V] = { // format: ON
    val zero = implicitly[Numeric[V]].zero

    val groups = itemSet.groups.map { group =>
      val stacks = group.stacks.map { stack =>
        val streams = stack.streams.map { stream =>
          val initial: Future[Seq[MultiValue[K, V]]] = stream.initialEntire.map { rows =>
            Span.prepare("tabularBlanksToZeros", parentSpan).time {
              rows.map { row =>
                val zeroedValues = row.value.map(_.getOrElse(zero))
                new MultiValue(row.argument, zeroedValues)
              }
            }
          }
          new BufferedMultiValueStream[K, V](initial, Observable.empty, None)
        }
        new BufferedMultiValueStack[K, V](streams)
      }
      new BufferedMultiValueGroup[K, V](stacks, group.name)
    }
    new BufferedMultiValueSet(groups)
  }

}
