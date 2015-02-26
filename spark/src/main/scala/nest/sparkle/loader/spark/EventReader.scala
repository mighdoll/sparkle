package nest.sparkle.loader.spark

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import nest.sparkle.loader.Loader.TaggedBlock

trait EventReader[K, V] {
  def events(input: String, sc: SparkContext): RDD[TaggedBlock]
}
