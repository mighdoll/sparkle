package nest.sparkle.datastream

import scala.concurrent.duration._
import nest.sparkle.util.StringToMillis._
import rx.lang.scala.Observable
import nest.sparkle.util.{Period, PeriodWithZone}
import org.joda.time.DateTimeZone
import nest.sparkle.util.TimeValueString.implicits._

/**
  */
object LargeReduction {
  
  def main(args:Array[String]) {
    byPeriod(30.seconds, "1 day")
  }
  
  def byPeriod(spacing:FiniteDuration, summaryPeriod:String)
      : DataArray[Long, Option[Long]] = {
    val stream = generateDataStream(spacing)
    val periodWithZone = PeriodWithZone(Period.parse(summaryPeriod).get, DateTimeZone.UTC)
    val reduced = stream.reduceByPeriod(periodWithZone, ReduceSum[Long]())
    val arrays = reduced.data.toBlocking.toList
    arrays.reduce(_ ++ _)
  }
  
  def toOnePart(spacing:FiniteDuration)
      : DataArray[Long, Option[Long]] = {
    val stream = generateDataStream(spacing)
    val reduced = stream.reduceToOnePart(ReduceSum[Long]())
    val arrays = reduced.data.toBlocking.toList
    arrays.reduce(_ ++ _)
  }
  
  def generateDataStream
      ( spacing:FiniteDuration = 30.seconds,
        blockSize: Int = 1000,
        start:String = "2013-01-01T00:00:00.000",
        until:String = "2014-01-01T00:00:00.000" )
      : DataStream[Long, Long] = {
    var startMillis = start.toMillis
    val untilMillis = until.toMillis
    
    
    val iter = new Iterator[DataArray[Long,Long]] {
      /** return a data array covering the next time period. Keys are
       *  spaced according to spacing above, and every value is 2 (long). */
      override def next():DataArray[Long,Long] = {
        val keys = 
          (0 until blockSize).iterator
            .map(_ => startMillis)
            .takeWhile {time => 
              startMillis = time + spacing.toMillis
              time < untilMillis 
            }
            .toArray
        
        val values = (0 until keys.length).map { _ => 2L }.toArray
        val result = DataArray(keys, values)
        result.foreach { keyValue => println(keyValue.timeValueString) }
        result
      }
      
      override def hasNext():Boolean = startMillis < untilMillis
    }
    
    val data = Observable.from(iter.toIterable)
    DataStream(data)
  }
  
}

