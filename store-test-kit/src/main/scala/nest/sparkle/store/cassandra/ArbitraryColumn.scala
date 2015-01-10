package nest.sparkle.store.cassandra

import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe._
import org.scalatest.prop.PropertyChecks
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import spray.util._
import nest.sparkle.store.{Event, WriteableStore}
import nest.sparkle.util.RandomUtil.randomAlphaNum
import scala.reflect.runtime.universe._

/** Utility for using scalacheck properties to generate Events */
object ArbitraryColumn extends PropertyChecks
{
  /** import this to allow generating Events in scalacheck generators */
  implicit def arbitraryEvent[T: Arbitrary, U: Arbitrary] = Arbitrary(eventGenerator[T, U]())

  private def eventGenerator[T: Arbitrary, U: Arbitrary]() = {
    for {
      key <- arbitrary[T]
      value <- arbitrary[U]
    } yield {
      Event(key, value)
    }
  }

  /** (currently unused, causes compiler problems in 2.10.3.  retry in 2.10.4 or 2.11) */
  def arbitraryColumn[T: TypeTag : Arbitrary : CanSerialize, U: TypeTag : Arbitrary : CanSerialize] // format: OFF
    (prefix: String, storage: WriteableStore)
      (implicit execution: ExecutionContext): (String, Seq[Event[T, U]]) =
  {
    // format: ON
    val eventListGenerator = arbitrary[List[Event[T, U]]]
    val eventsGenerator = eventListGenerator.asInstanceOf[Gen[List[Event[T, U]]]]
    val storedEventsGenerator =
      eventsGenerator.map { events =>
        val columnName = prefix + randomAlphaNum(4)
        val column = storage.writeableColumn[T, U](columnName).await
        column.write(events)
        (columnName, events)
      }
    storedEventsGenerator.sample.get
  }
}
