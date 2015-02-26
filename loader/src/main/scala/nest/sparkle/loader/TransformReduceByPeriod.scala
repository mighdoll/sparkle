package nest.sparkle.loader

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.language.existentials
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.util.{Failure, Success, Try}

import com.typesafe.config.Config
import org.joda.time.DateTimeZone
import rx.lang.scala.Observable
import spire.math.Numeric

import nest.sparkle.datastream._
import nest.sparkle.loader.Loader._
import nest.sparkle.measure.{Span, DummySpan}
import nest.sparkle.util.KindCast._
import nest.sparkle.util._

/**
 * A transformer that can reduce a column slice by taking a sum, mean, min, or max.
 */
class TransformReduceByPeriod(rootConfig: Config, transformerConfig: Config)
  extends LoadingTransformer with Log {

  val periodWithZone = {
    val timezone = if (transformerConfig.hasPath("timezone")) transformerConfig.getString("timezone") else "UTC"
    val period = Period.parse(transformerConfig.getString("period")) match {
      case Some(x) => x
      case None    => throw ConfigurationError(s"invalid period ${transformerConfig.getString("period")}")
    }
    PeriodWithZone(period, DateTimeZone.forID(timezone))
  }

  val fieldReductionTypes : Map[String, String] = transformerConfig.getConfigList("fields").asScala.toSeq.map { fieldConfig =>
    val reductionType = fieldConfig.getString("reduction-type")
    // test if reduction type is valid by using an Int
    numericReduction(reductionType, typeTag[Int]) match {
      case Success(_)   => fieldConfig.getString("field") -> reductionType
      case Failure(err) => throw err
    }
  }.toMap

  override def transform(sourceBlock: TaggedBlock2): Try[TaggedBlock2] = {
    val transformedBlock =
      sourceBlock.iterator.map { eventSlice: TaggedSlice2[_, _] =>

        /** name the existential _ key and value type parameters in the eventSlice as T and U so that we can keep them constant */
        def withFixedType[T, U](): Try[TaggedSlice2[T, U]] = {
          val keyType: TypeTag[T] = castKind(eventSlice.keyType)
          val valueType: TypeTag[U] = castKind(eventSlice.valueType)
          val dataArray = eventSlice.dataArray.asInstanceOf[DataArray[T, U]]
          val field = eventSlice.columnPath.substring(eventSlice.columnPath.lastIndexOf('/') + 1)
          val reducedDataArray =
            if (fieldReductionTypes.contains(field)) {
              for {
                reduction <- numericReduction[U](fieldReductionTypes.get(field).get, valueType)
                reducedDataArray <- reduce(keyType, valueType, dataArray, reduction)(keyType, valueType)
              } yield {
                reducedDataArray
              }
            } else {
              log.warn(s"unknown field $field, skipping reduce")
              Success(dataArray)
            }
          reducedDataArray.map(TaggedSlice2[T, U](eventSlice.columnPath, _)(keyType, valueType))
        }
        withFixedType()
      }

    TryUtil.firstFailureOrElseSuccessVector(transformedBlock)
  }

  /**
   * Returns a Reduction per the specified type ("reduceSum", "reduceMean", etc) and numeric TypeTag.
   */
  private def numericReduction[U](reductionType: String, valueType: TypeTag[_]): Try[IncrementalReduction[U]] = {
    RecoverNumeric.tryNumeric[U](valueType) match {
      case Success(valueNumeric) => {
        reductionType match {
          case "reduceSum"     => Success(ReduceSum()(valueNumeric))
          case "reduceMean"    => Success(ReduceMean()(valueNumeric))
          case "reduceAverage" => Success(ReduceMean()(valueNumeric))
          case "reduceMin"     => Success(ReduceMin()(valueNumeric))
          case "reduceMax"     => Success(ReduceMax()(valueNumeric))
          case _               => Failure(ConfigurationError(s"invalid reduction type $reductionType"))
        }
      }
      case Failure(err) => Failure(ReduceByPeriodTransformException(err))
    }
  }

  /**
   * Reduces the given DataArray, removing resulting None values and the corresponding keys.
   */
  private def reduce[T: TypeTag, U: TypeTag]
    ( keyType: TypeTag[T],
      valueType: TypeTag[U],
      dataArray: DataArray[T, U],
      reduction: IncrementalReduction[U] )
    : Try[DataArray[T, U]] = {

    RecoverNumeric.tryNumeric[T](keyType).flatMap { keyNumeric =>
      val keyClassTag: ClassTag[T] = ReflectionUtil.classTag[T](keyType)
      val valueClassTag: ClassTag[U] = ReflectionUtil.classTag[U](valueType)

      val reducedDataArray = reduceByPeriod(dataArray, periodWithZone, reduction)(keyType,
        keyNumeric, valueType, DummySpan)

      // get rid of None values and the corresponding keys
      val flattenedPairs = reducedDataArray.mapToArray { (key, value) =>
        value.map(Some(key, _)).getOrElse(None)
      }.flatten.toSeq

      Success(DataArray.fromPairs(flattenedPairs)(keyClassTag, valueClassTag))
    }
  }

  // TODO: figure out the right API for the generic DataArray/DataStream, so we don't need need this method
  /**
   * Convenience method to reduce a DataArray.
   */
  private def reduceByPeriod[K:TypeTag:Numeric, V:TypeTag]
   ( dataArray: DataArray[K, V],
     periodWithZone: PeriodWithZone,
     reduction: IncrementalReduction[V] )
   ( implicit parentSpan: Span )
    : DataArray[K, Option[V]] = {

    implicit val keyClassTag = ReflectionUtil.classTag[K](typeTag[K])
    implicit val valueClassTag = ReflectionUtil.classTag[V](typeTag[V])

    val dataStream = DataStream(Observable.from(Seq(dataArray)))
    val periodResult = dataStream.reduceByPeriod(periodWithZone, SoftInterval.empty, reduction, false)
    // it's okay to block here because we're working with a fixed DataArray
    val reducedDataArrays = periodResult.reducedStream.data.toBlocking.toList
    reducedDataArrays.reduce(_ ++ _)
  }
}

/** Indicates there's an issue transforming a block by reducing by period */
case class ReduceByPeriodTransformException(cause: Throwable) extends RuntimeException(cause)

