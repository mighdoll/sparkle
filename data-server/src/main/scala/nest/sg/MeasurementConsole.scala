package nest.sg

import java.text.NumberFormat

case class Interval(time:Long, duration:Long)

trait MeasurementConsole {
  self:StorageConsole =>
  /** return the span latency data for a given measurement name, grouped by trace id */
  def measurementsData(measurementName:String):Map[String,Intervals] = {
    case class MiniSpan(time:Long, name:String, traceId:String, duration:Long)

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
      val intervalSeq = spans.map{span => Interval(span.time, span.duration)}
      traceId -> Intervals(intervalSeq)
    }

    groupedIntervals
  }

  /** return a summary of the measurement data for the first traceId of a given measurement name */
  def lastMeasurement(measurementName:String):Intervals = {
    val data = measurementsData(measurementName)
    val startTimes = data.toVector.map{ case(traceId, intervals) =>
      traceId -> intervals.data.head.time
    }
    val sorted = startTimes.sortBy { case (traceId, time) => time}
    val lastId = sorted.last match { case (traceId, _) => traceId }
    data(lastId)
  }

}



case class Intervals(data:Seq[Interval]) {
  def printDuration() = {
    val micros = NumberFormat.getIntegerInstance.format(totalDuration / 1000)
    println(s"total duration: $micros microseconds")
  }

  def printTotalGap() = {
    val micros = NumberFormat.getIntegerInstance.format(startGaps.sum / 1000)
    println(s"total gap time: $micros microseconds")
  }

  def totalDuration:Long = data.map(_.duration).sum
  def startGaps:Seq[Long] = data.map(_.time).sliding(2).map{ case Seq(a, b) => b - a }.toVector
}
