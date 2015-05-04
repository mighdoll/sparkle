package nest.sparkle.time.transform


/** extractor to match a KeyValueRanges transform by string name */
object StandardObjectTransform {
  def unapply(transform: String): Option[ColumnTransform] = {
    transform.toLowerCase match {
      case "keyvalueranges" => Some(KeyValueRangesTransform)
      case _             => None
    }
  }
}