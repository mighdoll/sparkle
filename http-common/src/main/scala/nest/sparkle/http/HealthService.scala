package nest.sparkle.http

import spray.routing._

import nest.sparkle.util.Log

trait HealthService extends HttpService with Log {
  lazy val health: Route = {
    path("health") {
      dynamic {
        complete("ok")
      }
    }
  }

}
