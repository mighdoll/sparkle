package nest.sparkle.experiments.one

case class AImpl[K](elems: Seq[K]) extends Impl[K] {
  override def mapData[A](fn: K => A): AImpl[A] = {
    AImpl(elems map fn)
  }
}

trait Impl[K] {
  def mapData[A](fn: K => A): Impl[A]
}

case class Stack[K, S <: Impl[K]](streams: Seq[S]) {

  // this isn't quite right, we want the same subtype of Impl, an S with a new K
  //  def mapData[A, T <: Impl[A]](fn: K => A): Stack[A, T] = {
  //    val newStreams:Seq[Impl[A]] =
  //      streams.map { stream =>
  //        stream.mapData(fn)
  //      }
  //    Stack(newStreams)  // and this would require a cast
  //  }

}
