package nest.sparkle.time.transform

import scala.concurrent.Future
import nest.sparkle.time.protocol.RangeInterval
import nest.sparkle.measure.Span
import scala.concurrent.ExecutionContext
import nest.sparkle.store.Column
import scala.reflect.runtime.universe._

object FetchStreams {

  /** Read column data from a set of columns, returning data asynchronously in a StreamGroupSet.
    */
  def fetchData[K, V]( // format: OFF
        futureGroups: Future[Seq[ColumnGroup]],
        optRequestRanges: Option[Seq[RangeInterval[K]]],
        rangeExtend: Option[ExtendRange[K]],
        parentSpan:Option[Span]
      )(implicit execution: ExecutionContext)
      : Future[StreamGroupSet[K, V, AsyncWithRange]] = { // format: ON

    case class RangeAndExtended(range: RangeInterval[K], extended: RangeInterval[K])

    // extend the provided request range by the rangeExtend amount. Returns
    // returns a collection containing both the original requested range and the extended range.
    // (below we'll use the extended range to fetch the data, and will attach the requested range to the daa)
    val optRangeAndExtendeds: Option[Seq[RangeAndExtended]] =
      optRequestRanges.map { requestRanges =>
        val extended = rangeExtend.map(_.extend(requestRanges)).getOrElse(requestRanges)
        requestRanges.zip(extended).map {
          case (orig, extend) => RangeAndExtended(orig, extend)
        }
      }

    /** return an stream for each requested range in the provided column */
    def streamPerRange(column: Column[_, _]): Vector[AsyncWithRange[K, V]] = {
      val typedColumn = column.asInstanceOf[Column[K, V]]
      optRangeAndExtendeds match {
        case Some(rangeAndExtendeds) =>
          val streams =
            rangeAndExtendeds map {
              case RangeAndExtended(requestRange, extendedRange) =>
                val dataStream = FetchRanges.fetchRange(typedColumn, Some(extendedRange), parentSpan)
                // record the requested range with the data, not the extended range
                implicit val keyType = dataStream.keyType
                implicit val valueType = dataStream.valueType
                dataStream.copy(requestRange = Some(requestRange))
            }
          streams.toVector
        case None =>
          val dataStream = FetchRanges.fetchRange(typedColumn, None, parentSpan)
          Vector(dataStream)
      }
    }

    val result: Future[StreamGroupSet[K, V, AsyncWithRange]] =
      futureGroups.map { columnGroups =>
        val fetchedGroups: Vector[StreamGroup[K, V, AsyncWithRange]] =
          columnGroups.toVector.map { columnGroup =>
            val streamStacks: Vector[StreamStack[K, V, AsyncWithRange]] =
              columnGroup.columns.toVector.map { column =>
                val streams = streamPerRange(column)
                StreamStack(streams)
              }

            StreamGroup(columnGroup.name, streamStacks)
          }
        StreamGroupSet(fetchedGroups)
      }

    result
  }
}
