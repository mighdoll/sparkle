package nest.sparkle.time.transform

import nest.sparkle.store.Event
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.DateTimeZone

object ItemUtil {
  
  /** for debugging, interpret the interval as milliseconds and print as hours*/
  def datedToString[T, U](event: Event[T, U]): String = {
    def millisTimeString(millis: Long): String = {
      val dateTime = new DateTime(millis)
      dateTime.toString(ISODateTimeFormat.hourMinuteSecond.withZone(DateTimeZone.UTC))
    }
    val start = event.key.asInstanceOf[Long]
    val startString = millisTimeString(start)
    val valueString =
      event.value match {
        case l: Long => millisTimeString(start + l)
        case x       => x.toString
      }

    startString + "|" + "%-8s".format(valueString)
  }

}
