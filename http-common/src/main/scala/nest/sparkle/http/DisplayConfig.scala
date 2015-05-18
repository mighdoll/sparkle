package nest.sparkle.http

import scala.concurrent.{ExecutionContext, Future, future}
import scala.util.{Failure, Success}

import com.typesafe.config.{Config, ConfigRenderOptions}

import spray.http.MediaTypes.`application/json`
import spray.http.StatusCodes
import spray.routing._

import nest.sparkle.util.Log


/** Spray Routes to return the configuration. */
trait DisplayConfig
    extends Log 
{
  self: HttpService =>
  
  implicit def executionContext: ExecutionContext

  def rootConfig: Config
 
  /** Dump the configuration without comments */
  private lazy val conciseRoute: Route = {
    get {
      path("config") {
        compressResponseIfRequested() {
          onComplete(configConcise) { 
            case Success(s) =>
              respondWithMediaType(`application/json`) {
                complete(s)
              }
            case Failure(x) =>
              complete(StatusCodes.InternalServerError -> x.toString)
          }
        }
      }
    }
  }

  /** Dump the configuration with comments */
  private lazy val commentedRoute: Route = {
    get {
      path("config" / "commented") {
        compressResponseIfRequested() {
          onComplete(configCommented) { 
            case Success(s) =>
              complete(s)  // This isn't valid JSON due to the comments
             case Failure(x)  =>
              complete(StatusCodes.InternalServerError -> x.toString)
          }
        }
      }
    }
  }
 
  /** all config routes */
  lazy val configRoutes: Route = { // format: OFF
    conciseRoute ~
    commentedRoute
  } // format: ON

  private lazy val renderConcise = ConfigRenderOptions.concise().setFormatted(true)
  
  private def configConcise: Future[String] = Future {
    rootConfig.root.render(renderConcise)
  }

  private def configCommented: Future[String] = Future {
    rootConfig.root.render()
  }
}