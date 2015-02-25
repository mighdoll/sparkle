package nest.sparkle.util

import scala.concurrent.Promise

import org.scalatest.{Matchers, FunSuite}
import rx.lang.scala.Observable
import nest.sparkle.util.ObservableFuture._
import nest.sparkle.util.FutureAwait.Implicits._
import scala.concurrent.duration._

class TestObservableUtil extends FunSuite with Matchers {
  
  test("headTail") {
    val obs = Observable.from(Seq(1,2,3))
    val (head, tail) = ObservableUtil.headTail(obs)
    head.toBlocking.toList shouldBe Seq(1)
    tail.toBlocking.toList shouldBe Seq(2,3)
  }

  test("headTail on empty observable") {
    val (head,tail) = ObservableUtil.headTail(Observable.empty)
    head.toBlocking.toList shouldBe Seq()
    tail.toBlocking.toList shouldBe Seq()
  }

  test("headTail on ongoing observable") {
    val obs = Observable.from(Seq(3,2)) ++ Observable.interval(20.milliseconds)
    val (head,tail) = ObservableUtil.headTail(obs)
    head.toBlocking.toList shouldBe Seq(3)
    tail.take(4).toBlocking.toList shouldBe Seq(2,0,1,2)
  }

  test("reduceSafe on empty observable") {
    val result = ObservableUtil.reduceSafe(Observable.empty){(a:Int,b:Int) => a}
    result.toBlocking.toList shouldBe Seq()
  }

  test("reduceSafe") {
    val obs = Observable.from(Seq(1,2,3))
    val result = ObservableUtil.reduceSafe(obs){ _ + _ }
    result.toBlocking.toList shouldBe Seq(6)
  }

}
