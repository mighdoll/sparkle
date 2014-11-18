package nest.sparkle.measure

import com.typesafe.config.Config
import nest.sparkle.util.Log
import scala.concurrent.forkjoin.ThreadLocalRandom
import nest.sparkle.util.RandomUtil

/** a measurement of a span of time. Subclasses encode various states of a Span: Unstarted, Started, Completed, etc.) 
 *  All Spans are immutable */
protected trait Span {
  def name: String
  def traceId: TraceId
  def spanId: SpanId
  def parentId: Option[SpanId]
  def measurements: Measurements
  def opsReport: Boolean
}

/** convenient ways to create a Span for timing */
object Span {
  /** return an UnstartedSpan without a parent Span (e.g. parent is a trace) */
  def prepareRoot(name: String, traceId: TraceId, opsReport:Boolean = false) // format: OFF
      (implicit measurements: Measurements): UnstartedSpan = {  // format: ON
     UnstartedSpan(name = name, traceId = traceId, spanId = SpanId.create(), parentId = None, 
         annotations = Seq(), opsReport = opsReport, measurements = measurements) 
  }
  
  /** return an UnstartedSpan with a parent Span */
  def apply(name: String, opsReport:Boolean = false)(implicit parent: Span): UnstartedSpan = {
    val myName = s"${parent.name}.$name"    
    UnstartedSpan(name = myName, traceId = parent.traceId, spanId = SpanId.create(),
      parentId = Some(parent.spanId), annotations = Seq(), opsReport = opsReport,
      measurements = parent.measurements)
  }

  /** return a StartedSpan that starts now */
  def start // format: OFF
      (name: String, parentSpan: Span, opsReport:Boolean = false)
      : StartedSpan = { // format: ON 
    val myName = s"${parentSpan.name}.$name"    
    StartedSpan(name = myName, spanId = SpanId.create(), traceId = parentSpan.traceId, parentId = Some(parentSpan.spanId),
      start = NanoSeconds.current(), annotations = Seq(), 
      opsReport = opsReport, measurements = parentSpan.measurements)
  }
  
  /** return a StartedSpan that starts now */
  def startNoParent // format: OFF
      (name: String, traceId: TraceId, opsReport:Boolean = false)
      (implicit measurements: Measurements)
      : StartedSpan = { // format: ON 
    StartedSpan(name = name, spanId = SpanId.create(), traceId = traceId, parentId = None,
      start = NanoSeconds.current(), annotations = Seq(), 
      opsReport = opsReport, measurements = measurements)
  }
}


/** a span that's completed and ready to record */
case class CompletedSpan(
    name: String,
    traceId: TraceId,
    spanId: SpanId,
    parentId: Option[SpanId],
    start: EpochMicroseconds,
    duration: NanoSeconds,
    annotations: Seq[Annotation],
    opsReport: Boolean,
    measurements: Measurements) extends Span with Log {
}

case class UnstartedSpan(
    name: String,
    traceId: TraceId,
    spanId: SpanId = SpanId.create(),
    parentId: Option[SpanId] = None,
    annotations: Seq[Annotation] = Seq(),
    opsReport: Boolean,
    measurements: Measurements) extends Span with Log {

  /** Start the timing clock. 
   *  Note: the caller is responsible for calling complete() on the returned StartedSpan (to end the timing and report). */
  def start(): StartedSpan = {
    val start = NanoSeconds.current()
    StartedSpan(name = name, traceId = traceId, spanId = spanId, parentId = parentId, start = start,
      annotations = annotations, opsReport = opsReport, measurements = measurements)
  }
  
  /** Time a function and report the results. */
  def time[T](fn: => T):T = {
    val started = start()
    val result = fn
    started.complete()
    result
  }
}

case class StartedSpan(
    name: String,
    traceId: TraceId,
    spanId: SpanId,
    parentId: Option[SpanId],
    start: NanoSeconds,
    annotations: Seq[Annotation],
    opsReport: Boolean,
    measurements: Measurements) extends Span {

  /** complete a timing, reporting the total time through the measurements gateway (to e.g. graphite and .csv) */
  def complete() = {
    val end = NanoSeconds.current()
    val duration = NanoSeconds(end.value - start.value)
    val startMicros = EpochMicroseconds.fromNanos(start)
    val completed = CompletedSpan(name = name, traceId = traceId, spanId = spanId, parentId = parentId,
      start = startMicros, duration = duration, annotations = annotations, 
      opsReport = opsReport, measurements = measurements)
    measurements.publish(completed)
  }
}

object DummySpan extends Span {
  override val name = "dummy"
  override val traceId = TraceId("dummyId")
  override val spanId = SpanId(-1)
  override val parentId = None
  override val opsReport = false
  override val measurements = DummyMeasurements
}

/** a nanosecond value. Note the underlying storage is a signed long, so durations over 250 years will get weird */
case class NanoSeconds(val value: Long) extends AnyVal

/** return the current nanoseconds relative the jvm start */
object NanoSeconds {
  def current(): NanoSeconds = new NanoSeconds(System.nanoTime())
}

/** microseconds since midnight January 1, 1970 UTC */
case class EpochMicroseconds(val value: Long) extends AnyVal

/** microseconds since midnight January 1, 1970 UTC */
object EpochMicroseconds {
  def now(): EpochMicroseconds = {
    val micros = CalibratedNanos.approxEpochNanos() / 1000L
    new EpochMicroseconds(micros)
  }
  def fromNanos(nanos:NanoSeconds):EpochMicroseconds = {
    CalibratedNanos.toEpochMicros(nanos)    
  }
}

/** milliseconds since midnight January 1, 1970 UTC */
case class EpochMilliseconds(val value:Long) extends AnyVal

/** The id for a single top level request (typically a request initiated by an external client into our distributed system) */
case class TraceId(val value: String) extends AnyVal 

object TraceId {
  def create(): TraceId = new TraceId(RandomUtil.randomAlphaNum(8))
}

/** The id for a single duration record */
case class SpanId(val value: Long) extends AnyVal

/** The id for a single duration record */
object SpanId {
  def create(): SpanId = new SpanId(ThreadLocalRandom.current().nextLong)
}

/** (currently unused) a timestamped event occuring within a Span */
sealed abstract class Annotation(time: NanoSeconds)

/** (currently unused) a timestamped label occuring within a Span */
case class StringAnnotation(_time: NanoSeconds, name: String) extends Annotation(_time)

