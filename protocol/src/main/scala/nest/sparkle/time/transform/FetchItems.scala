package nest.sparkle.time.transform

import nest.sparkle.time.protocol.RangeInterval
import nest.sparkle.store.Column
import nest.sparkle.store.Event
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import nest.sparkle.time.transform.ItemStreamTypes._
import scala.reflect.runtime.universe._
import nest.sparkle.measure.UnstartedSpan
import nest.sparkle.measure.Span
import scala.concurrent.Promise
import scala.util.Success
import rx.lang.scala.Observable

/** read column data a set of columns, returning data asynchronously in a FetchedGroupSet
  * (A FetchedGroupSet is intended to be a convenient structure for subsequent transformation).
  */
object FetchItems {

  /** read column data a set of columns, returning data asynchronously in a FetchedGroupSet
    * (A FetchedGroupSet is intended to be a convenient structure for subsequent transformation).
    */
  def fetchItems[K]( // format: OFF
        futureGroups: Future[Seq[ColumnGroup]],
        optRequestRanges: Option[Seq[RangeInterval[K]]],
        rangeExtend: Option[ExtendRange[K]],
        parentSpan:Option[Span]
      )(implicit execution: ExecutionContext)
      : Future[FetchedGroupSet[K]] = { // format: ON
    val track = TrackObservable()
    case class RangeAndExtended(range: RangeInterval[K], extended: RangeInterval[K])

    // extend the provided request range by the rangeExtend amount. Resutls
    // in a collection containing both the original requested range and the extended range.
    val optRangeAndExtendeds: Option[Seq[RangeAndExtended]] =
      optRequestRanges.map { requestRanges =>
        val extended = rangeExtend.map(_.extend(requestRanges)).getOrElse(requestRanges)
        requestRanges.zip(extended).map {
          case (orig, extend) => RangeAndExtended(orig, extend)
        }
      }

    /** return an stream for each requested range in the provided column */
    def streamPerRange(column: Column[_, _]): Seq[RawItemStream[K]] = {
      val typedColumn = column.asInstanceOf[Column[K, Any]]
      optRangeAndExtendeds match {
        case Some(rangeAndExtendeds) =>
          rangeAndExtendeds map {
            case RangeAndExtended(requestRange, extendedRange) =>
              val stream: RawItemStream[K] = SelectRanges.fetchRange(typedColumn, Some(extendedRange), parentSpan)
              implicit val keyType = stream.keyType
              // record the requested range with the data, not the extended range
              new RawItemStream[K](stream.initial, stream.ongoing, Some(requestRange))
          }
        case None =>
          Seq(SelectRanges.fetchRange(typedColumn, None, parentSpan))
      }
    }

    val result: Future[FetchedGroupSet[K]] =
      futureGroups.map { columnGroups =>
        val fetchedGroups: Seq[RawItemGroup[K]] =
          columnGroups.map { columnGroup =>
            val stacks: Seq[RawItemStack[K]] =
              columnGroup.columns.map { column =>
                val streams = streamPerRange(column)
                new RawItemStack(streams)
              }

            new RawItemGroup[K](stacks, columnGroup.name)
          }
        new FetchedGroupSet(fetchedGroups)
      }

    result
  }

}