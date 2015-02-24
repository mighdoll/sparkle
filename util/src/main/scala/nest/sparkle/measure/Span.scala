package nest.sparkle.measure

import nest.sparkle.util.Log
import scala.concurrent.forkjoin.ThreadLocalRandom
import nest.sparkle.util.RandomUtil

sealed trait Measurement {
  def name: String
  def traceId: TraceId
  def spanId: SpanId
  def parentId: Option[SpanId]
  def measurements: Measurements
  def level: ReportLevel
}

sealed trait CompletedMeasurement extends Measurement


/** a measurement of a span of time. Subclasses encode various states of a Span: Unstarted, Started, Completed, etc.)
  * All Spans are immutable
  */
sealed trait Span extends Measurement

/** convenient ways to create a Span for timing */
object Span {
  val ignoreTraceId = TraceId("__ignore")

  /** return an UnstartedSpan without a parent Span (e.g. parent is a trace) */
  def prepareRoot(name: String, traceId: TraceId = TraceId.create(), level: ReportLevel = Info) // format: OFF
      (implicit measurements: Measurements): UnstartedSpan = { // format: ON
    level match {
      case Inherit  => throw new RootSpanLevelException  // This is a programming error
      case _        =>
    }
    UnstartedSpan(name = name, traceId = traceId, spanId = SpanId.create(), parentId = None,
      annotations = Seq(), level = level, measurements = measurements)
  }

  /** return an UnstartedSpan with a parent Span */
  def apply(name: String, level: ReportLevel = Inherit)(implicit parent: Span): UnstartedSpan = {
    val myName = s"${parent.name}.$name"
    val myLevel = level match {
      case Inherit  => parent.level
      case _        => level
    }
    UnstartedSpan(name = myName, traceId = parent.traceId, spanId = SpanId.create(),
      parentId = Some(parent.spanId), annotations = Seq(), level = myLevel,
      measurements = parent.measurements)
  }

  /** return a StartedSpan that starts now */
  def start // format: OFF
      (name: String, parentSpan: Span, level: ReportLevel = Info)
      : StartedSpan = { // format: ON 
    val myName = s"${parentSpan.name}.$name"
    StartedSpan(name = myName, spanId = SpanId.create(), traceId = parentSpan.traceId, parentId = Some(parentSpan.spanId),
      start = NanoSeconds.current(), annotations = Seq(),
      level = level, measurements = parentSpan.measurements)
  }

  /** return a StartedSpan that starts now */
  def startNoParent // format: OFF
      (name: String, traceId: TraceId = TraceId.create(), level: ReportLevel = Info)
      (implicit measurements: Measurements)
      : StartedSpan = { // format: ON 
    StartedSpan(name = name, spanId = SpanId.create(), traceId = traceId, parentId = None,
      start = NanoSeconds.current(), annotations = Seq(),
      level = level, measurements = measurements)
  }
}

/** a span that's completed and ready to record */
case class CompletedSpan protected[measure](
    name: String,
    traceId: TraceId,
    spanId: SpanId,
    parentId: Option[SpanId],
    start: EpochMicroseconds,
    duration: NanoSeconds,
    annotations: Seq[Annotation],
    level: ReportLevel,
    measurements: Measurements) extends CompletedMeasurement with Log {
}


case class UnstartedSpan protected[measure](
    name: String,
    traceId: TraceId,
    spanId: SpanId = SpanId.create(),
    parentId: Option[SpanId] = None,
    annotations: Seq[Annotation] = Seq(),
    level: ReportLevel,
    measurements: Measurements) extends Span with Log {

  /** Start the timing clock.
    * Note: the caller is responsible for calling complete() on the returned StartedSpan (to end the timing and report).
    */
  def start(): StartedSpan = {
    val start = NanoSeconds.current()
    StartedSpan(name = name, traceId = traceId, spanId = spanId, parentId = parentId, start = start,
      annotations = annotations, level = level, measurements = measurements)
  }

  /** Time a function and report the results. */
  def time[T](fn: => T): T = {
    val started = start()
    val result = fn
    started.complete()
    result
  }
}

case class StartedSpan protected[measure](
    name: String,
    traceId: TraceId,
    spanId: SpanId,
    parentId: Option[SpanId],
    start: NanoSeconds,
    annotations: Seq[Annotation],
    level: ReportLevel,
    measurements: Measurements) extends Span {

  /** complete a timing, reporting the total time through the measurements gateway (to e.g. graphite and .csv) */
  def complete():Unit = {
    if (traceId != Span.ignoreTraceId) {
      val end = NanoSeconds.current()
      val duration = NanoSeconds(end.value - start.value)
      val startMicros = EpochMicroseconds.fromNanos(start)
      val completed = CompletedSpan(name = name, traceId = traceId, spanId = spanId, parentId = parentId,
        start = startMicros, duration = duration, annotations = annotations,
        level = level, measurements = measurements)
      measurements.publish(completed)
      }
  }
}

object DummySpan extends Span {
  override val name = "dummy"
  override val traceId = TraceId("dummyId")
  override val spanId = SpanId(-1)
  override val parentId = None
  override val level = Info
  override val measurements = DummyMeasurements
}

/** a nanosecond value. Note the underlying storage is a signed long, so durations over 250 years will get weird */
case class NanoSeconds(value: Long) extends AnyVal

/** return the current nanoseconds relative the jvm start */
object NanoSeconds {
  def current(): NanoSeconds = new NanoSeconds(System.nanoTime())
}

/** microseconds since midnight January 1, 1970 UTC */
case class EpochMicroseconds(value: Long) extends AnyVal

/** microseconds since midnight January 1, 1970 UTC */
object EpochMicroseconds {
  def now(): EpochMicroseconds = {
    val micros = CalibratedNanos.approxEpochNanos() / 1000L
    new EpochMicroseconds(micros)
  }
  def fromNanos(nanos: NanoSeconds): EpochMicroseconds = {
    CalibratedNanos.toEpochMicros(nanos)
  }
}

/** milliseconds since midnight January 1, 1970 UTC */
case class EpochMilliseconds(value: Long) extends AnyVal
case class Milliseconds(value: Long) extends AnyVal

/** The id for a single top level request (typically a request initiated by an external client into our distributed system) */
case class TraceId(value: String) extends AnyVal

object TraceId {
  def create(): TraceId = new TraceId(RandomUtil.randomAlphaNum(8))
}

/** The id for a single duration record */
case class SpanId(value: Long) extends AnyVal // TODO rename to MeasurementId

/** The id for a single duration record */
object SpanId {
  def create(): SpanId = new SpanId(ThreadLocalRandom.current().nextLong)
}

/** (currently unused) a timestamped event occuring within a Span */
sealed abstract class Annotation(time: NanoSeconds)

/** (currently unused) a timestamped label occuring within a Span */
case class StringAnnotation(_time: NanoSeconds, name: String) extends Annotation(_time)

/** Thrown when using Inherit for a root span */
class RootSpanLevelException extends Exception("Root Spans can not have level Inherit")

/** a sampled metric value */
case class Gauged[T](
    name: String,
    traceId: TraceId,
    spanId: SpanId = SpanId.create(),
    parentId: Option[SpanId] = None,
    annotations: Seq[Annotation] = Seq(),
    level: ReportLevel,
    measurements: Measurements,
    start: EpochMicroseconds,
    value:T) extends CompletedMeasurement

object Gauged {
  /** report a sampled metric value */
  def apply[T](name:String, value:T, level: ReportLevel = Inherit)(implicit parent: Span): Unit = {
    val classOfValue = value.getClass
    // for now require Long compatible type until we write to multiple gauge files for each value type
    assert(
      classOfValue == classOf[Long] || classOfValue == classOf[java.lang.Long] ||
        classOfValue == classOf[Int] || classOfValue == classOf[java.lang.Integer] ||
        classOfValue == classOf[Short] || classOfValue == classOf[java.lang.Short] ||
        classOfValue == classOf[Byte] || classOfValue == classOf[java.lang.Byte]
    )
    val gauged = Gauged[T](name = name, traceId = parent.traceId, level = level,
      measurements = parent.measurements, start = EpochMicroseconds.now, value = value)
    parent.measurements.publish(gauged)
  }
}