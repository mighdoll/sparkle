package nest.sparkle.util

/** whitelist/blacklist enum */
sealed trait FilterType { def name: String }
case object Whitelist extends FilterType { val name = "Whitelist" }
case object Blacklist extends FilterType { val name = "Blacklist" }

object FilterType {

  /** All filter types */
  val filterTypes: Set[FilterType] = Set(Whitelist, Blacklist)

  /** Convert a string to the corresponding FilterType */
  def withName(str: String): Option[FilterType] = {
    filterTypes.find { filterType =>
      filterType.name.equalsIgnoreCase(str)
    }
  }
}
