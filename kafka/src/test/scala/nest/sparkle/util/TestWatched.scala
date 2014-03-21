package nest.sparkle.util

import org.scalatest.FunSuite
import org.scalatest.Matchers
import rx.lang.scala.Observable
import rx.lang.scala.Observer
import scala.collection.mutable
import rx.lang.scala.subjects.ReplaySubject
import scala.concurrent.duration._

/** a simple watchable class of three numbers of testing */
case class TestWatchable() extends Watched[Int] {
  def publicData: Observer[Int] = data
  val items = Observable.items(1, 2, 3)
  def start() {
    // RX is there a clearer idiom for connecting observer to observable?
    items.subscribe(publicData)
  }
}

/** create a Watch that preserves the report data for test convenience */
object TestWatch {
  def apply(): Watch[Int] = {
    val fullReport = ReplaySubject[WatchReport[Int]]()
    Watch[Int](fullReport = fullReport)
  }
}

class TestWatched extends FunSuite with Matchers {

  def withTestSubscription[T](filter: Option[Int => Boolean] = None) // format: OFF
      (fn: Watch[Int]=>T): T = { // format: ON

    val watchable = TestWatchable()
    val watch = TestWatch().copy(filter = filter)
    watchable.watch(watch)
    watchable.start()
    fn(watch)
  }

  test("get all items") {
    withTestSubscription() { watch =>
      watch.report.toBlockingObservable.toList shouldBe List(1, 2, 3)
      watch.fullReport.toBlockingObservable.toList shouldBe List(WatchStarted(), WatchData(1), WatchData(2), WatchData(3))
    }
  }

  test("get selected items") {
    def filter(a: Int): Boolean = { a == 2 }

    withTestSubscription(Some(filter _)) { watch =>
      watch.report.toBlockingObservable.toList shouldBe List(2)
      watch.fullReport.toBlockingObservable.toList shouldBe List(WatchStarted(), WatchData(2))
    }
  }

  test("subscriptions expire") {
    val watchable = TestWatchable()
    val watch = TestWatch().copy(duration = 10.milliseconds)
    watchable.watch(watch)
    watchable.items.delay(11.milliseconds).subscribe(watchable.publicData)
    watch.fullReport.toBlockingObservable.toList shouldBe List(WatchStarted())
  }

  test("subscriptions can be cancelled") {
    val watchable = TestWatchable()
    val watch = TestWatch()
    watchable.watch(watch)

    watch.subscription.unsubscribe()

    watchable.start()
    watch.fullReport.toBlockingObservable.toList shouldBe List(WatchStarted())
  }

  test("example") {
    def example(source: Watched[Int]) {
      // filter for values that equal 0
      val watch = Watch[Int](filter = Some{ (a: Int) => a == 0})
      source.watch(watch)
      
      // print the filtered result stream
      watch.report.subscribe { value => println(value) }
    }
    
    val watched = TestWatchable()
    example(watched)
    watched.start()
  }

}