package nest.sparkle.util

import org.scalacheck.Gen.Parameters

object ScalaCheckUtils {
  
  /** convenience routine for specifiying size to a generator */
  def withSize(size:Int):Parameters = Parameters.default.withSize(size)  

}