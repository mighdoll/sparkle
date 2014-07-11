package nest.sparkle.util

object StableGroupBy {
  
  implicit class Implicit[T](coll:Traversable[T]) {
  /** groupBy that preserves the order of the underlying sequences (and so returns a Seq rather than a map) */
    def stableGroupBy[U](fn: T => U): Traversable[(U, Seq[T])] = { 
      val groups = coll.groupBy(fn)
      val keysInOrder = coll.map(fn(_)).toSeq.distinct
      keysInOrder.map { key => (key, groups(key).toSeq) }
    }
  }

}