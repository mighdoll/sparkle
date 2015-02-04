package nest.sparkle.util
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.ISODateTimeFormat

/** Utility for pretty printing a time,value tuple. Time is interpreted as epoch milliseconds. */
object TimeValueString {
  lazy val formatter = ISODateTimeFormat.dateHourMinute.withZone(DateTimeZone.UTC)
  object implicits {
    
    /** Utility for pretty printing a time,value tuple. Time is interpreted as epoch milliseconds. */
    implicit class TupleTimeValueString[V](keyValue: (Long, V)) {
      def timeValueString: String = {
        val (key, value) = keyValue
        timeValueToString(key, value)
      }
    }
  }

  def timeValueToString[V](key:Long, value:V):String = {
    def millisTimeString(millis: Long): String = {
      val dateTime = new DateTime(millis)
      dateTime.toString(formatter)
    }
    val startString = millisTimeString(key)

    startString + "->" + value.toString
  }
}
