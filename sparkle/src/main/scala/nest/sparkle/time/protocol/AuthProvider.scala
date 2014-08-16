package nest.sparkle.time.protocol

import scala.concurrent.Future
import com.typesafe.config.Config
import nest.sparkle.util.Instance
import nest.sparkle.util.Log

/** Subclasses should override AuthProvider to authenticate protocol realm parameters (id and auth),
  * and produce an Authorizer on demand. The Authorizer which will be used to 
  * authorize authenticated clients.
  *
  * Subclasses should implement a single argument constructor that takes a Config parameter
  */
trait AuthProvider {
  def authenticate(realmParameters: Option[RealmToServer]): Future[Authorizer]
}

object AuthProvider extends Log {
  /** create the AuthProvider specified in the .conf file. */
  def instantiate(rootConfig:Config):AuthProvider = {
    val providerClass = rootConfig.getString("sparkle-time-server.auth.provider")
    log.info(s"instantiating AuthProvider: $providerClass")
    val authProvider = Instance.byName[AuthProvider](providerClass)(rootConfig)
    authProvider
  }
}

case object AuthenticationFailed extends RuntimeException
case object AuthenticationMalformed extends RuntimeException
case object AuthenticationMissing extends RuntimeException
