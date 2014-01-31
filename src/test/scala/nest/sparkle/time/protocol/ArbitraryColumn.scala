package nest.sparkle.time.protocol
import org.scalacheck.Arbitrary.arbitrary
import nest.sparkle.store.Event
import org.scalacheck.Arbitrary
import nest.sparkle.store.Storage
import nest.sparkle.store.ram.WriteableRamColumn
import nest.sparkle.store.WriteableStorage
import scala.reflect.runtime.universe._
import nest.sparkle.store.ram.WriteableRamStore
import nest.sparkle.util.RandomUtil.randomAlphaNum
import spray.util._
import scala.concurrent.ExecutionContext
import org.scalatest.prop.PropertyChecks
import org.scalacheck.Gen

object ArbitraryColumn extends PropertyChecks {
  val eventGen = for {
    key <- arbitrary[Long]
    value <- arbitrary[Double]
  } yield {
    Event(key, value)
  }
  implicit val arbitraryEvent = Arbitrary(eventGen)

  def arbitraryColumn[T: TypeTag: Ordering, U: TypeTag](prefix: String, storage: WriteableRamStore) // format: OFF
      (implicit execution: ExecutionContext): Gen[Seq[Event[T, U]]] = { // format: ON
    val eventListGenerator = arbitrary[List[Event[Long, Double]]] // SCALA make generator work for T,U    
    val eventsGenerator = eventListGenerator.asInstanceOf[Gen[List[Event[T, U]]]]
    val storedEventsGenerator =
      eventsGenerator.map { events =>
        val column = storage.writeableColumn[T, U](prefix + randomAlphaNum(4)).await
        column.write(events)
        events
      }
    storedEventsGenerator
  }
}