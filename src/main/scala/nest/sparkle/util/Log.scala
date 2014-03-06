package nest.sparkle.util
import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.slf4j.Logger

/** Adds the lazy val 'log' of type [[$Logger]] to the class into which this trait is mixed.
  *
  * @define Logger com.typesafe.scalalogging.slf4j.Logger
  */

trait Log {
  protected lazy val log: Logger = Logger(LoggerFactory.getLogger(getClass.getName))
}
