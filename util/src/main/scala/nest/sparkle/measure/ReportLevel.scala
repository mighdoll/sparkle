package nest.sparkle.measure


/** level of detail in latency measurements reports */
sealed trait ReportLevel {
  def level:Int
}

/** basic operational timing, e.g. user requests */
case object Info extends ReportLevel {
  override val level = 3
}

/** more detail timings, for performance diagnostics. e.g. for individual stages in processing a user request. */
case object Detail extends ReportLevel {
  override val level = 4 
}

/** very fine grain timings, for developers. e.g. timing of processing individual blocks of data */
case object Trace extends ReportLevel {
  override val level = 5
}
