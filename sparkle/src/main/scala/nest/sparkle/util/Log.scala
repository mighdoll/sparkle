package nest.sparkle.util
import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.slf4j.Logger

/** Adds a lazy val 'log' of type com.typesafe.scalalogging.slf4j.Logger to the class into which this trait is mixed.
  */
trait Log {
  protected lazy val log: Logger = Logger(LoggerFactory.getLogger(getClass.getName))
}
