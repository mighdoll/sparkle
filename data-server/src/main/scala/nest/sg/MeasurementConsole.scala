package nest.sg

import java.text.NumberFormat
import nest.sparkle.util.PrettyNumbers.implicits._

case class Interval(time:Long, duration:Long)
case class TraceIntervals(traceId:String, intervals:Intervals)
case class NamedTraceIntervals(name:String, traceIntervals:Seq[TraceIntervals])


trait MeasurementConsole {
  self:StorageConsole =>
  /** return the span latency data for a given measurement name, grouped by trace id,
    * in time order of the start time of each group. */
  def measurementsData(measurementName:String):Seq[TraceIntervals] = {
    case class MiniSpan(time: Long, name: String, traceId: String, duration: Long)

    val durations = columnData[Long]("spans/duration")
    val traceIds = columnData[String]("spans/traceId").values.iterator
    val names = columnData[String]("spans/name").values.iterator
    val spans =
      durations.map { case (time, duration) =>
        val traceId = traceIds.next()
        val name = names.next()
        MiniSpan(time, name, traceId, duration)
      }


    val matchingSpans = spans.filter(_.name == measurementName)
    val groupedSpans = matchingSpans.groupBy(_.traceId)
    val groupedIntervals = groupedSpans.map { case (traceId, spans) =>
      val intervalSeq = spans.map { span => Interval(span.time, span.duration)}
      TraceIntervals(traceId, Intervals(intervalSeq))
    }
    groupedIntervals.toVector.sortBy(_.intervals.data.head.time)
  }

  /** return a summary of the measurement data for the first traceId of a given measurement name */
  def lastMeasurement(measurementName:String):Intervals = {
    measurementsData(measurementName).last.intervals
  }

  def allIntervals():Seq[NamedTraceIntervals] = {
    allMeasurements().map { name =>
      NamedTraceIntervals(name, measurementsData(name))
    }.toSeq
  }

  def allMeasurements():Set[String] = {
    columnData[String]("spans/name").values.toSet
  }

}

case class Intervals(data:Seq[Interval]) {
  /** total duration of all spans, in microseconds */
  def totalDuration:Long = data.map(_.duration).sum / 1000

  /** total time between the previous Span ending and this one starting, in microseconds */
  def startEndGaps:Seq[Long] = {
    val startEnds = data.map{ interval =>
      val end = interval.time + interval.duration / 1000
      (interval.time, end)
    }

    startEnds.length match {
      case 0 => Seq()
      case 1 => Seq(0L)
      case n =>
        startEnds.sliding(2).map { case Seq((_,aEnd), (bStart, _)) =>
          bStart - aEnd
        }.toVector
    }
  }

  /** gap between start and end time, in microseconds */
  def startToEnd: Long = data.last.time + (data.last.duration / 1000) - data.head.time

  def printTotalDuration():Unit = {
    println(s"  total duration: ${totalDuration.pretty} microseconds")
  }

  def printTotalGaps(): Unit = {
    println(s"  total gap time: ${startEndGaps.sum.pretty} microseconds")
  }

  def printStartToEnd(): Unit ={
    println(s"  start to end time: ${startToEnd.pretty} microseconds")
  }


  def printAll(): Unit = {
    printTotalDuration()
    printTotalGaps()
    printStartToEnd()
  }
}
