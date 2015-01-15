package nest.sparkle.util

import org.joda.time.format.ISODateTimeFormat

object StringToMillis {
  private val format = ISODateTimeFormat.dateTimeParser.withZoneUTC
    
  /** for test convenience enhance String with a toMillis method that converts iso 8601 strings
    * into milliseconds
    */
  implicit class IsoDateString(isoDateString: String) {
    def toMillis = format.parseDateTime(isoDateString).getMillis
  }

}