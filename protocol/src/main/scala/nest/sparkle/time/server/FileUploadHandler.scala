package nest.sparkle.time.server

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.Path
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._
import spray.http.HttpHeaders.{RawHeader, _}
import spray.http._
import spray.http.parser.HttpParser
import spray.io.CommandWrapper

import akka.actor._
import org.jvnet.mimepull.{MIMEMessage, MIMEPart}

import nest.sparkle.loader.SingleFileLoader
import nest.sparkle.store.WriteableStore
import nest.sparkle.util.Log

// adapted from spray.io example code

/** spray.io handler for chunked file upload into the store. Create this actor to handle the upload
  * of a single file after the the ChunkedRequestStart message has been received by the io layer. */
class FileUploadHandler(store:WriteableStore, batchSize:Int, client: ActorRef, start: ChunkedRequestStart)
      extends Actor with Log {

  import start.request._
  client ! CommandWrapper(SetRequestTimeout(Duration.Inf)) // cancel timeout, uploading is slow

  val uploadedMime = File.createTempFile("chunked-receive", ".tmp", new File("/tmp"))
  uploadedMime.deleteOnExit()
  val mimeFileStream = new FileOutputStream(uploadedMime)
  val Some(HttpHeaders.`Content-Type`(ContentType(multipart: MultipartMediaType, _))) = header[HttpHeaders.`Content-Type`]
  val boundary = multipart.parameters("boundary")
  import context.dispatcher
  val loader = new SingleFileLoader(store, batchSize)

  log.info(s"Got start of chunked request $method $uri with multipart boundary '$boundary' writing to $uploadedMime")
  var bytesWritten = 0L

  def receive = {
    case c: MessageChunk =>
      log.debug(s"Got ${c.data.length} bytes of chunked request $method $uri")

      mimeFileStream.write(c.data.toByteArray)
      bytesWritten += c.data.length

    case e: ChunkedMessageEnd =>
      log.info(s"Got end of chunked request $method $uri")
      mimeFileStream.close()
      val files = messageToFiles(uploadedMime)
      files.foreach { file =>
        loadFileIntoStore(file.toPath)
      }

      client ! HttpResponse(status = 200,
                            headers = List(`Access-Control-Allow-Origin`(AllOrigins)),  // TODO fixme
                            entity = "")
      client ! CommandWrapper(SetRequestTimeout(2.seconds)) // reset timeout to original value
      uploadedMime.delete()
      context.stop(self)
  }

  def messageToFiles(mimeFile:File): Seq[File] = {
    val message = new MIMEMessage(new FileInputStream(mimeFile), boundary)

    val parts = message.getAttachments.asScala.toVector

    parts.map { part =>
      val fileName = fileNameForPart(part).getOrElse("unknown")
      val file = new File(s"/tmp/$fileName")
      log.debug("writing uploaded file $file")
      part.moveTo(file)
      file.deleteOnExit()
      file
    }
  }

  def loadFileIntoStore(path:Path): Future[Unit]= {
    loader.loadFile(path, path.toString, path.getParent())
  }

  def fileNameForPart(part: MIMEPart): Option[String] =
    for {
      dispHeader <- part.getHeader("Content-Disposition").asScala.toSeq.lift(0)
      Right(disp: `Content-Disposition`) = HttpParser.parseHeader(RawHeader("Content-Disposition", dispHeader))
      name <- disp.parameters.get("filename")
    } yield name

}