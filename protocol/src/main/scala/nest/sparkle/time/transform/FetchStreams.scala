package nest.sparkle.time.transform

import scala.Vector
import scala.concurrent.{ExecutionContext, Future}

import nest.sparkle.datastream.{AsyncWithRange, StreamGroup, StreamGroupSet, StreamStack}
import nest.sparkle.measure.Span
import nest.sparkle.store.Column
import nest.sparkle.time.protocol.RangeInterval

object FetchStreams {

  /** Read column data from a set of columns, returning data asynchronously in a StreamGroupSet.
    */
  def fetchData[K, V] // format: OFF
      ( futureGroups: Future[Seq[ColumnGroup]],
        optRequestRanges: Option[Seq[RangeInterval[K]]],
        rangeExtend: Option[ExtendRange[K]],
        parentSpan:Option[Span] )
      ( implicit execution: ExecutionContext )
      : Future[StreamGroupSet[K, V, AsyncWithRangeColumn]] = { // format: ON

    case class RangeAndExtended(range: RangeInterval[K], extended: RangeInterval[K])

    // extend the provided request range by the rangeExtend amount. Returns
    // returns a collection containing both the original requested range and the extended range.
    // (below we'll use the extended range to fetch the data, and will
    // attach the requested range to the data)
    val optRangeAndExtendeds: Option[Seq[RangeAndExtended]] =
      optRequestRanges.map { requestRanges =>
        val extended = rangeExtend.map(_.extend(requestRanges)).getOrElse(requestRanges)
        requestRanges.zip(extended).map {
          case (orig, extend) => RangeAndExtended(orig, extend)
        }
      }

    /** return an stream for each requested range in the provided column */
    def streamPerRange(column: Column[_, _]): Vector[AsyncWithRangeColumn[K, V]] = {
      val typedColumn = column.asInstanceOf[Column[K, V]]
      optRangeAndExtendeds match {
        case Some(rangeAndExtendeds) =>
          val streams =
            rangeAndExtendeds map {
              case RangeAndExtended(requestRange, extendedRange) =>
                val dataStream = FetchRanges.fetchRange(typedColumn, Some(extendedRange), parentSpan)
                // record the requested range with the data, not the extended range
                replaceRange(dataStream, requestRange)
            }
          streams.toVector
        case None =>
          val dataStream = FetchRanges.fetchRange(typedColumn, None, parentSpan)
          Vector(dataStream)
      }
    }

    /** copy a data stream, replacing the requestRange */
    def replaceRange(dataStream:AsyncWithRangeColumn[K,V], range:RangeInterval[K]): AsyncWithRangeColumn[K,V] = {
      implicit val keyType = dataStream.keyType
      implicit val valueType = dataStream.valueType
      new AsyncWithRangeColumn(
        initial = dataStream.initial,
        ongoing = dataStream.ongoing,
        requestRange = Some(range.softInterval),
        column = dataStream.column
      )
    }

    val result: Future[StreamGroupSet[K, V, AsyncWithRangeColumn]] =
      futureGroups.map { columnGroups =>
        val fetchedGroups: Vector[StreamGroup[K, V, AsyncWithRangeColumn]] =
          columnGroups.toVector.map { columnGroup =>
            val streamStacks: Vector[StreamStack[K, V, AsyncWithRangeColumn]] =
              columnGroup.columns.toVector.map { column =>
                val streams:Vector[AsyncWithRangeColumn[K, V]] = streamPerRange(column)
                // SCALA this is a flaw with our current encoding of TwoPartStream
                // the problem is that TwoPartStream takes a stream implementation type parameter
                // AsyncWithRangeColumn extends AsyncWithRange which in turn extends TwoPartStream
                // AsyncWithRange sets the stream implementation type in TwoPartStream
                // but really we want the subtype AsyncWithRangeColumn to set the stream
                // implementation type.
                //
                // for now, we cast our way out.
                val stackWrongType:StreamStack[K,V,AsyncWithRange] = StreamStack(streams)
                stackWrongType.asInstanceOf[StreamStack[K, V, AsyncWithRangeColumn]]
              }

            StreamGroup(columnGroup.name, streamStacks)
          }
        StreamGroupSet(fetchedGroups)
      }

    result
  }
}
