package nest.sparkle.measure


/** Utility for using the nanonsecond clock for wall clock time. Java's nanosecond timer is a free-running timer,
  * not aligned to wall clock time. currentTimeMillis is aligned, however, so we
  */
object CalibratedNanos {
  /** calculate alignment of nanoTime clock to currentTimeMillis clock (which spins a cpu for a while) */
  def initialize() = offsetToEpochNanos

  protected[measure] def approxEpochNanos(): Long = {
    System.nanoTime() + offsetToEpochNanos
  }

  /** Find nanoTime normalization offset:
    * return the nanoseconds to add to nanoTime() such that 0 is unix epoch time
    */
  private lazy val offsetToEpochNanos: Long = {
    def nanoOffset(): Long = {
      val sinceEpochNanosNow = System.currentTimeMillis() * 1000000L
      val nanoTimeNow = System.nanoTime()
      val nanoClockOffset = sinceEpochNanosNow - nanoTimeNow
      nanoClockOffset
    }

    // try it a few times to get the smallest possible offset.
    val offsets = Iterator.range(1, 100000) map { _ => nanoOffset() }
    val closest = offsets.minBy { a => Math.abs(a) }
    if (Math.abs(closest) < 1000000L) {
      // assume less than 1msec means the clocks are aligned.  (as on jvm7 on macos, but not on linux)
      0
    } else {
      closest
    }
  }

  /** convert a nanoTime clock value to approximate millis since the epoch */
  protected[measure] def toEpochMillis(nanos: NanoSeconds): EpochMilliseconds = {
    EpochMilliseconds((nanos.value + offsetToEpochNanos) / 1000000L)
  }
  
  /** convert a nanoTime clock value to approximate millis since the epoch */
  protected[measure] def toEpochMicros(nanos: NanoSeconds): EpochMicroseconds = {
    EpochMicroseconds((nanos.value + offsetToEpochNanos) / 1000L)
  }

}
