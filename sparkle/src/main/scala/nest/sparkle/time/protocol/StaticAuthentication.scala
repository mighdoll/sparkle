package nest.sparkle.time.protocol

import com.typesafe.config.Config
import scala.concurrent.Future

case class StaticAuthConfigError(msg: String) extends RuntimeException(msg)

/** Authenticate with a single static password for all clients. Authenticated clients
 *  are authorized for all requests. */
class StaticAuthentication(rootConfig: Config) extends AuthProvider {
  val configKey = "sparkle-time-server.auth.password"
  val optPassword = Option(rootConfig.getString(configKey))
  val staticPassword = optPassword.getOrElse(throw StaticAuthConfigError(s"$configKey missing from .conf file"))

  override def authenticate(optRealm: Option[Realm]): Future[Authorizer] = {
    optRealm match {
      case Some(Realm(_, _, Some(auth))) if auth == staticPassword => Future.successful(AllColumnsAuthorized)
      case Some(Realm(_, _, None))                                 => Future.failed(AuthenticationMissing)
      case None                                                    => Future.failed(AuthenticationMissing)
      case _                                                       => Future.failed(AuthenticationFailed)
    }
  }
}


/** Skip authentication and simply authorize all requests */
class AllAuthorized(rootConfig: Config) extends AuthProvider {
  override def authenticate(optRealm: Option[Realm]): Future[Authorizer] = {
    Future.successful(AllColumnsAuthorized)
  }
}
