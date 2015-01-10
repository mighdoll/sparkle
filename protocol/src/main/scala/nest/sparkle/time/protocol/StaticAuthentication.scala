package nest.sparkle.time.protocol

import com.typesafe.config.Config
import scala.concurrent.Future

import nest.sparkle.util.ConfigUtil.configForSparkle

case class StaticAuthConfigError(msg: String) extends RuntimeException(msg)

/** Authenticate with a single static password for all clients. Authenticated clients
 *  are authorized for all requests. */
class StaticAuthentication(rootConfig: Config) extends AuthProvider {
  val sparkleConfig = configForSparkle(rootConfig)
  val configKey = "auth.password"
  val optPassword = Option(sparkleConfig.getString(configKey))
  val staticPassword = optPassword.getOrElse(throw StaticAuthConfigError(s"$configKey missing from sparkle config"))

  override def authenticate(optRealm: Option[RealmToServer]): Future[Authorizer] = {
    optRealm match {
      case Some(RealmToServer(_, _, Some(auth))) if auth == staticPassword => Future.successful(AllColumnsAuthorized)
      case Some(RealmToServer(_, _, None))                                 => Future.failed(AuthenticationMissing)
      case None                                                    => Future.failed(AuthenticationMissing)
      case _                                                       => Future.failed(AuthenticationFailed)
    }
  }
}


/** Skip authentication and simply authorize all requests */
class AllAuthorized(rootConfig: Config) extends AuthProvider {
  override def authenticate(optRealm: Option[RealmToServer]): Future[Authorizer] = {
    Future.successful(AllColumnsAuthorized)
  }
}
