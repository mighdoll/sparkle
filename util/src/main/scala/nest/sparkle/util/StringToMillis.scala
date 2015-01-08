package nest.sparkle.util

object StringToMillis {
  /** for test convenience enhance String with a toMillis method that converts iso 8601 strings
    * into milliseconds
    */
  implicit class IsoDateString(isoDateString: String) {
    import com.github.nscala_time.time.Implicits._

    def toMillis = isoDateString.toDateTime.getMillis
  }

}