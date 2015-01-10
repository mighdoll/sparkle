package nest.sparkle.time.transform


/** extractor to match a DomainRange transform by string name */
object StandardObjectTransform {
  def unapply(transform: String): Option[ColumnTransform] = {
    transform.toLowerCase match {
      case "domainrange" => Some(DomainRangeTransform)
      case _             => None
    }
  }
}