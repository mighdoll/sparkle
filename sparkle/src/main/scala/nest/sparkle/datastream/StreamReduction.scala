package nest.sparkle.datastream

import spire.implicits._
import spire.math._
import nest.sparkle.util.BooleanOption._


/** implements an incremental reduction that can freeze and unfreeze its state */
trait Reduction[V, F] {
  /** add one more value to the reduction */
  def accumulate(value: V): Unit

  /** optionally return the current reduced sum */
  def currentTotal: Option[V]

  /** return a frozen copy of this reduction, suitable for re-instantation with unfreeze */
  def freeze(): F // TODO get rid of freeze/unfreeze

  /** apply the frozen state */
  def unfreeze(frozen: F): Reduction[V, F]

  /** make a new instance of this type */ // probably put in a companion typeclass SCALA
  def newInstance(): Reduction[V, F]
}

/** accumulate by choosing the larger value */
case class ReduceMax[V: Numeric]() extends Reduction[V, Option[V]] {
  private var max: Option[V] = None

  override def accumulate(value: V): Unit = {
    max match {
      case Some(currentMax) if (value > currentMax) => max = Some(value)
      case Some(_)                                  => // do nothing
      case None                                     => max = Some(value)
    }
  }

  override def currentTotal: Option[V] = max

  override def freeze(): Option[V] = max

  override def unfreeze(frozen: Option[V]): ReduceMax[V] = {
    val newCopy = newInstance()
    newCopy.max = frozen
    newCopy
  }

  override def newInstance():ReduceMax[V] = ReduceMax[V]()
}

/** accumulate by choosing the larger value */// TODO DRY with Sum and Max
case class ReduceMin[V: Numeric]() extends Reduction[V, Option[V]] {
  private var min: Option[V] = None

  override def accumulate(value: V): Unit = {
    min match {
      case Some(currentMin) if (value < currentMin) => min = Some(value)
      case Some(_)                                  => // do nothing
      case None                                     => min = Some(value)
    }
  }

  override def currentTotal: Option[V] = min

  override def freeze(): Option[V] = min

  override def unfreeze(frozen: Option[V]): ReduceMin[V] = {
    val newCopy = newInstance()
    newCopy.min = frozen
    newCopy
  }

  override def newInstance():ReduceMin[V] = ReduceMin[V]()
}


/** accumulate by summing the values */
case class ReduceSum[V: Numeric]() extends Reduction[V, Option[V]] {
  private var total = implicitly[Numeric[V]].zero
  private var started = false

  override def accumulate(value: V): Unit = {
    started = true
    total += value
  }

  override def currentTotal: Option[V] = started.toOption.map{_ =>
    total
  }

  override def freeze(): Option[V] = started.toOption.map(_=> total)

  override def unfreeze(frozen: Option[V]): ReduceSum[V] = {
    val newCopy = newInstance()
    frozen.foreach { frozenTotal =>
      newCopy.total = frozenTotal
      newCopy.started = true
    }

    newCopy
  }

  override def newInstance():ReduceSum[V] = ReduceSum[V]()
}


case class ReduceMeanState[V](total:V, count:Int)
/** accumulate by calculating the numeric mean of the values */
case class ReduceMean[V: Numeric]() extends Reduction[V, ReduceMeanState[V]] {
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

  override def freeze() = ReduceMeanState(total, count)

  override def unfreeze(frozen: ReduceMeanState[V]): ReduceMean[V] = {
    val newCopy = newInstance()
    newCopy.total = frozen.total
    newCopy.count = frozen.count
    newCopy
  }

  override def newInstance():ReduceMean[V] = ReduceMean[V]()
}