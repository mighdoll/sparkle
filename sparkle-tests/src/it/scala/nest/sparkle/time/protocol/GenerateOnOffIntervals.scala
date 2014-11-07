package nest.sparkle.time.protocol
import nest.sparkle.util.StringToMillis._
import scala.concurrent.duration._
import java.util.concurrent.ThreadLocalRandom

/** a single record intended for MultiIntervalSum tests */
case class MultiRecord(millis: Long, one: Boolean, two: Boolean, three: Boolean, four: Boolean, five: Boolean, six: Boolean, a: Boolean, b: Boolean) {

  /** return a copy of this MultiRecord with each boolean value flipped with probability flipChance (0 to 1.0) */
  def randomEvolve(flipChance: Double): MultiRecord = {
    
    /** possibly flip a single boolean, with probablity flipChance */
    def flip(start:Boolean): Boolean = {
      if (ThreadLocalRandom.current().nextDouble() < flipChance)
        !start
      else 
        start      
    }
    
    this.copy(
      one = flip(one),
      two = flip(two),
      three = flip(three),
      four = flip(four),
      five = flip(five),
      six = flip(six),
      a = flip(a),
      b = flip(b)
    )
  }

  /** return a copy of this MultiRecords with the time changed */
  def later(delta:Long):MultiRecord = copy(millis = millis + delta)
}

/** generate a random MultiRecord sequence for testing */
object GenerateOnOffIntervals {
  
  /** generate a random sequence of on/off intervals 
   *  @param flipChance - each boolean value has this percentage chance of chaning with each new record 
   *  @param spacing - time between each generated record */
  def generate(flipChance:Double = .02, spacing:Duration = 25.seconds): Iterator[MultiRecord] = {
    val start = "2014-11-06T00:00:00.000Z".toMillis
    val delta = spacing.toMillis
    var last = MultiRecord(start, false, false, false, false, false, false, false, false)
    
    /** save the record as the last one generated, and return the provided record */
    def save(record:MultiRecord):MultiRecord = {
      last = record
      record      
    }
    
    Iterator.continually {
      save(last.randomEvolve(flipChance).later(delta))
    }
  }
}