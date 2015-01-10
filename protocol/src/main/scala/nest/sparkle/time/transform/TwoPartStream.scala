package nest.sparkle.time.transform
import scala.language.higherKinds
import scala.reflect.runtime.universe._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import nest.sparkle.time.protocol.RangeInterval
import rx.lang.scala.Observable
import nest.sparkle.core.DataArray
import scala.{ specialized => spec }

// format: OFF
// TODO revise this for the new version
/** The data transformations specified by protocol requests work on data from Columns in the Store. 
  * Transforms may operate on multiple slices of data from one column, on multiple columns, and on 
  * multiple groups of columns. The containers of the data are organized heirarchically, as follows: 
  *
  * StreamGroupSet - data from multiple groups of columns
  *   StreamGroup - data from multiple columns
  *     StreamStack - data from one or more slices of a single column 
  *       TwoPartStream - data from a single slice from a single column
  *                    implementations of TwoPartStream have two collections of data
  *         initial - all data available the time of the request
  *         ongoing - items arriving after the request (normally only for open ended ranges)
  *         
  *         DataArray - both initial and ongoing data contain sample data packed into DataArrays
  *         					  The DataArrays are delivered asynchronously: buffered in a Future or streamed in an Observable
  * 
  * Conventions for type parameter letters:
  *   K - key type
  *   V - value type
  *   S - TwoPartStream typeclass proxy type
  *  
  *   B - target value type (e.g. for mapData)
  *   T - target TwoPartStream typeclass proxy type (e.g. for mapStream)
  * 
  * There are two core challenges in structuring the container data structures: 1) mapping higher level
  * functions through the layers of the containment heirarchy: e.g. we want to enable users of the 
  * library to convert on/off boolean values to duration lengths without having to worry about the 
  * four levels of containment and various subtypes involved. 2) enable users of the library to
  * operate memory-efficiently on arrays in a generic way: e.g. library users should be able to 
  * 'sum' the values in an array efficiently, regardless of whether the elements are longs, shorts or doubles.
  * Read on for a discussion of those two issues.
  * 
  * -- 1) Working with nested containers --
  * To apply higher level functions to contained elements, several problems must be solved:
  *   . Container subtypes: The type signature of the elements and the TwoPartStream subtype must be exposed. 
  *     This keeps usage type safe. Functions that demand to operate on Long, or only on buffered streams
  *     should fail at compile time if applied to doubles, or non-buffered streams.
  *   . Nested building: The library needs a way to construct new TwoPartStream subtype instances, e.g. when mapping
  *     to from Long to Boolean elements types. 
  *   . Minimal boilerplate, especially for clients of the library. Naiive solutions that e.g. push 
  *     the nested building problem onto users of the library are unnattractive.
  * 
  * This is similar to the challenge tackled by scala collection libraries: the scala collection library, 
  * scalaz, debox, psp-std, etc.
  * 
  * I'm aware of three broad approaches to this problem: higher kinded Builders, F-bounded types, and 
  * higher kinded proxying Typeclasses. At the moment, we're using the proxying typeclass approach favored 
  * by e.g. scalaz and spire. (or at least my imitation of that technique)  
  * // SCALA is this the best approach? f-bounded seems difficult, but builders might be sufficient. 
  * // builders wouldn't as easily expose custom functionality on contained types though..
  * 
  * -- 2) Efficient Array functions --
  * JVM primitive types (long, double, etc.) are efficient for both computation and storage. Naiive 
  * implementations that work on generic types will tend to create boxed versions of the primitive values
  * and make overloaded function calls to do basic arithmatic and comparison (e.g. add). 
  * 
  * Our current approach is to use spire and specialization for efficient functions on primitive arrays. The 
  * basic approach is to a) use specialization to ask the compiler to create duplicate primitive-optimized 
  * versions of performance critical inner loops (and the calling chain required to trigger those inner loops) and b) rely
  * on spire's carefully tuned functions and typeclasses (themselves specialized) for generic operations.
  */ 

// format: ON



/** a typeclass proxy for a stream of DataArrays. */
// TODO this probably needs a Typetag for both key and value so that transorms can map on what they need
trait TwoPartStream[K, V, StreamImpl[_, _]] {
  me: StreamImpl[K, V] =>
    
  def keyType: TypeTag[K]
  def valueType: TypeTag[V]
  
  /** return the TwoPartStream implementation type */
  def self: StreamImpl[K, V] = me
    
  def mapData[B: TypeTag] // format: OFF
    (fn: DataArray[K, V] => DataArray[K, B])
    (implicit execution: ExecutionContext)
    : TwoPartStream[K, B, StreamImpl] // format: ON

  def doOnEach(fn: DataArray[K, V] => Unit): TwoPartStream[K, V, StreamImpl]
  def mapInitial[A](fn: DataArray[K, V] => A): Observable[A]
  def mapOngoing[A](fn: DataArray[K, V] => A): Observable[A]

  def plus(other: TwoPartStream[K, V, StreamImpl]): TwoPartStream[K, V, StreamImpl]
}

/** a collection of DataStreams, e.g. from the multiple ranges coming from one column */
case class StreamStack[K, V, S[_, _]] // format: OFF
    (streams: Vector[TwoPartStream[K, V, S]]) { // format: ON
}

/** a collection of StreamStacks, e.g. a set of columns that should should be aggregated together */
case class StreamGroup[K, V, S[_, _]] // format: OFF
    (name:Option[String], streamStacks:Vector[StreamStack[K,V,S]]) { // format: ON
}

/** A collection of StreamGroups. (Normally the StreamGroupSet will be aggregated
  * together in a single response to a protocol client.)
  */
case class StreamGroupSet[K, V, S[_, _]] // format: OFF
    (streamGroups:Vector[StreamGroup[K, V, S]]) { // format: ON

  /** Apply a function to all the DataArrays in all the contained DataStreams and
    * return the resulting StreamGroupSet
    */
  def mapData[B: TypeTag] // format: OFF
      (fn: DataArray[K, V] => DataArray[K, B])
      (implicit execution:ExecutionContext)
      : StreamGroupSet[K, B, S] = { // format: ON
    mapStreams(_.mapData(fn))
  }

  /** */
  def mapStreams[A, B, T[_, _]] // format: OFF
        (fn: TwoPartStream[K, V, S] => TwoPartStream[A, B, T])
        : StreamGroupSet[A, B, T] = { // format: ON
    val newGroups = streamGroups.map { group =>
      val newStacks = group.streamStacks.map { stack =>
        val newStreams = stack.streams map fn
        StreamStack(newStreams)
      }
      StreamGroup(group.name, newStacks)
    }
    new StreamGroupSet(newGroups)
  }

  /** Return a flattened collection of all the contained DataStreams. */
  def allStreams: Seq[TwoPartStream[K, V, S]] = {
    for {
      group <- streamGroups
      stack <- group.streamStacks

      // concatenate the streams in the stack
      combinedStream <- stack.streams.reduceLeftOption { (a, b) => a.plus(b) }
    } yield {
      combinedStream
    }
  }

}