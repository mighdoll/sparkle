package nest.sparkle.loader

import scala.language.existentials
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.util.Try

import nest.sparkle.datastream.DataArray
import nest.sparkle.loader.Loader._
import nest.sparkle.util.{FieldsDescriptor, Flags, Log, ReflectionUtil, TryUtil}

/**
 * A transformer that can convert column slices with boolean values to a single column
 * slice with the boolean values stored as a Long (ie Flags object).
 */
abstract class TransformFlags[T <: Flags :TypeTag](flagsFactory: () => T)
  extends LoadingTransformer with Log {

  implicit val flagsClassTag: ClassTag[T] = ReflectionUtil.classTag[T]

  val fieldsDescriptor: FieldsDescriptor = flagsFactory().fields

  /** column name to use for the slice of Flags objects */
  def flagsColumn: String

  override def transform(sourceBlock: TaggedBlock): Try[TaggedBlock] = {

    var optFlagsDataset: Option[String] = None // dataset for the Flags

    // split into slices that are and aren't part of the Flags
    val (flagSlices, otherSlices) = sourceBlock.partition { slice =>
      val idx = slice.columnPath.lastIndexOf('/')
      val field = slice.columnPath.substring(idx + 1)
      fieldsDescriptor.positionOfField(field).isDefined
    }

    // convert to FlagUpdates
    val flagUpdatesIter: Iterator[Try[Seq[FlagUpdate]]] =
      flagSlices.iterator.map { slice =>
        val idx = slice.columnPath.lastIndexOf('/')
        val field = slice.columnPath.substring(idx + 1)
        val optPosition = fieldsDescriptor.positionOfField(field)
        assert(optPosition.isDefined)
        val position = optPosition.get
        val dataset = slice.columnPath.substring(0, idx)
        optFlagsDataset match {
          case Some(flagsDataset) => assert(flagsDataset == dataset)
          case None               => optFlagsDataset = Some(dataset)
        }
        for {
          dataArray <- Try(slice.dataArray.asInstanceOf[DataArray[Any,Boolean]])
        } yield {
          dataArray.map { case (key, value: Boolean) => FlagUpdate(key, position, value) }
        }
      }

    TryUtil.firstFailureOrElseSuccessVector(flagUpdatesIter).map { flagUpdates =>
      if (flagUpdates.isEmpty) {
        otherSlices
      } else {
        // Flags-ify
        val flagsByKey: Map[Any, T] = flagUpdates.flatten.groupBy(_.key).map { case (key, flagUpdates) =>
          (key, flagUpdates.foldLeft(flagsFactory())((flags, flagUpdate) =>
            flags.updatedFlags(flagUpdate.position, flagUpdate.value).asInstanceOf[T]))
        }
        assert(optFlagsDataset.isDefined)
        otherSlices :+ TaggedSlice[Any, T](s"${optFlagsDataset.get}/$flagsColumn", DataArray.fromPairs(flagsByKey))
      }
    }
  }

}

/** Indicates there's an issue transforming a block to a slice of Flags */
case class TransformFlagsException(msg: String) extends RuntimeException(msg)

/** Holds the bit value at a given position for the Flags id'ed by key */
case class FlagUpdate(key: Any, position: Int, value: Boolean)
