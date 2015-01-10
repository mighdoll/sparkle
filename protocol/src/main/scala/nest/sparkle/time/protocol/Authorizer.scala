package nest.sparkle.time.protocol

trait Authorizer {
  // TODO add api to authorize at the request level (not the columnPath level).
}

case object AllColumnsAuthorized extends Authorizer {
//  override def authorizeColumn(columnPath: String): Future[Unit] = Future.successful()
}

case class ColumnForbidden(msg: String) extends RuntimeException(msg)
case object AllColumnsForbidden extends Authorizer {
//  override def authorizeColumn(columnPath: String): Future[Unit] = Future.failed(ColumnForbidden(columnPath))
}
