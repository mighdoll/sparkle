package nest.sparkle.datastream

import spire.implicits._
import spire.math._
import nest.sparkle.util.BooleanOption._


/** implements an reduction operation controlled by external iteration */
trait IncrementalReduction[V] {
  /** add one more value to the reduction */
  def accumulate(value: V): Unit

  /** optionally return the current reduced combined value */ 
  def currentTotal: Option[V]  

  /** make a new instance of this type */ // probably put in a companion typeclass SCALA
  def newInstance(): IncrementalReduction[V]
}

/** accumulate by choosing the larger value */
case class ReduceMax[V: Numeric]() extends IncrementalReduction[V] {
  private var max: Option[V] = None

  override def accumulate(value: V): Unit = {
    max match {
      case Some(currentMax) if (value > currentMax) => max = Some(value)
      case Some(_)                                  => // do nothing
      case None                                     => max = Some(value)
    }
  }

  override def currentTotal: Option[V] = max

  override def newInstance():ReduceMax[V] = ReduceMax[V]()
}

/** accumulate by choosing the larger value */// TODO DRY with Sum and Max
case class ReduceMin[V: Numeric]() extends IncrementalReduction[V] {
  private var min: Option[V] = None

  override def accumulate(value: V): Unit = {
    min match {
      case Some(currentMin) if (value < currentMin) => min = Some(value)
      case Some(_)                                  => // do nothing
      case None                                     => min = Some(value)
    }
  }

  override def currentTotal: Option[V] = min

  override def newInstance():ReduceMin[V] = ReduceMin[V]()
}

/** accumulate by summing the values */
case class ReduceSum[V: Numeric]() extends IncrementalReduction[V] {
  private var total = implicitly[Numeric[V]].zero
  private var started = false

  override def accumulate(value: V): Unit = {
    started = true
    total += value
  }

  override def currentTotal: Option[V] = started.toOption.map{_ =>
    total
  }

  override def newInstance():ReduceSum[V] = ReduceSum[V]()
}

/** accumulate by calculating the numeric mean of the values */
case class ReduceMean[V: Numeric]() extends IncrementalReduction[V] {
  private var total = implicitly[Numeric[V]].zero
  private var started = false
  private var count = 0

  override def accumulate(value: V): Unit = {
    started = true
    count += 1
    total += value
  }

  override def currentTotal: Option[V] = started.toOption.map{_ =>
      total / count
    }

  override def newInstance():ReduceMean[V] = ReduceMean[V]()
}

/** accumulate by calculating the count of the values.  */
// LATER don't cast to the value type, or require that the values be numeric
case class ReduceCount[V:Numeric]() extends IncrementalReduction[V] {
  private var count = 0

  override def accumulate(value: V): Unit = {
    count += 1
  }

  override def currentTotal: Option[V] = {
    if (count == 0) None
    else Some(implicitly[Numeric[V]].fromInt(count))
  }

  override def newInstance(): ReduceCount[V] = ReduceCount[V]()
}
