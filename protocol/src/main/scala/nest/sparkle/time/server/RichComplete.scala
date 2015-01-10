package nest.sparkle.time.server

import spray.routing.Route
import spray.routing.Directives
import scala.concurrent.ExecutionContext
import spray.httpx.marshalling.Marshaller
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure
import java.nio.file.NoSuchFileException
import spray.http.StatusCodes
import java.io.FileNotFoundException
import nest.sparkle.store.{ColumnNotFound, DataSetNotFound}

trait RichComplete extends Directives {
  implicit def executionContext: ExecutionContext
  
  /** complete a future result, if necessary mapping some sparkle errors into appropriate http errors */
  def richComplete[T](future: Future[T])(implicit marshaller: Marshaller[T]): Route = {
    onComplete(future) {
      _ match {
        case Success(s) =>
          complete(s)
        case Failure(notFound: NoSuchFileException) =>
          complete(StatusCodes.NotFound -> notFound.getMessage)
        case Failure(notFound: FileNotFoundException) =>
          complete(StatusCodes.NotFound -> notFound.getMessage)
        case Failure(notFound: ColumnNotFound) =>
          complete(StatusCodes.NotFound -> notFound.getMessage)
        case Failure(notFound: DataSetNotFound) =>
          complete(StatusCodes.NotFound -> notFound.getMessage)
        case Failure(x) =>
          complete(x)
      }
    }
  }

}
