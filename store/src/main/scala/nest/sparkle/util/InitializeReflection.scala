package nest.sparkle.util

import nest.sparkle.store.cassandra.RecoverCanSerialize

/** workaround for reflection concurrency bug in scala 2.10.x
  * When running the unit tests, I've seen intermittent failures like:
  *  java.lang.NoClassDefFoundError: Could not initialize class nest.sparkle.util.RecoverJsonFormatDollarImplicitsDollar
  * This seems to fix things.
  */
object InitializeReflection {
  lazy val init = {
    RecoverOrdering.standardOrderings.foreach { _ => }
    RecoverJsonFormat.jsonFormats.foreach { _ => }
    RecoverCanSerialize.canSerializers.foreach { _ => }
    RecoverFractional.Implicits.standardFractional.foreach { _ => }
    RecoverNumeric.standardNumeric.foreach { _ => }
  }
}