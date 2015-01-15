package nest.sparkle.util
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.ISODateTimeFormat

/** Utility for pretty printing a time,value tuple. Time is interpreted as epoch milliseconds. */
object TimeValueString {
  object implicits {
    
    /** Utility for pretty printing a time,value tuple. Time is interpreted as epoch milliseconds. */
    implicit class TupleTimeValueString[V](keyValue: (Long, V)) {
      def timeValueString: String = {
        def millisTimeString(millis: Long): String = {
          val dateTime = new DateTime(millis)
          dateTime.toString(ISODateTimeFormat.dateHourMinute.withZone(DateTimeZone.UTC))
        }
        val (key, value) = keyValue
        val startString = millisTimeString(key)

        startString + "->" + value.toString
      }
    }
  }
}
