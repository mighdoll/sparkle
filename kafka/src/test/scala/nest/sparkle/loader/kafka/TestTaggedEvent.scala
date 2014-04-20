package nest.sparkle.loader.kafka

import scala.reflect.runtime.universe._

import org.scalatest.{ FunSuite, Matchers }

import nest.sparkle.store.Event

class TestTaggedEvent extends FunSuite with Matchers {
  test("match LongDouble") {
    val tagged = TaggedColumn("longDouble", typeTag[Long], typeTag[Double], Nil)

    tagged match {
      case LongIntEvents(events)      => fail("should have matched LongDoubleEvents")
      case LongLongEvents(events)     => fail("should have matched LongDoubleEvents")
      case LongStringEvents(events)   => fail("should have matched LongDoubleEvents")
      case StringDoubleEvents(events) => fail("should have matched LongDoubleEvents")
      case LongDoubleEvents(events)   =>
      case _                          => fail("should have matched LongDoubleEvents")
    }
  }
  
  test("match LongInt") {
    val tagged = TaggedColumn("longDouble", typeTag[Long], typeTag[Int], Nil)

    tagged match {
      case LongLongEvents(events)     => fail("should have matched LongIntEvents")
      case LongStringEvents(events)   => fail("should have matched LongIntEvents")
      case StringDoubleEvents(events) => fail("should have matched LongIntEvents")
      case LongDoubleEvents(events)   => fail("should have matched LongIntEvents")
      case LongIntEvents(events)      => 
      case _                          => fail("should have matched LongIntEvents")
    }
  }
  
}