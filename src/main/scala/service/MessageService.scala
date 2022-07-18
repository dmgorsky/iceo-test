//package service
//
//import cats.effect.IO
//import com.iceo.model.{Message, MsgNotFoundError}
//import com.iceo.repository.MessageRepository
//import fs2.Stream
//import io.circe.generic.auto._
//import io.circe.syntax._
//import io.circe.{Decoder, Encoder}
//import org.http4s.Status.{NoContent, NotFound}
//import org.http4s.circe._
//import org.http4s.dsl.Http4sDsl
//import org.http4s.headers.{Location, `Content-Type`}
//import org.http4s.{HttpRoutes, MediaType, Uri}
//import org.slf4j.LoggerFactory
//
//class MessageService(repository: MessageRepository) extends Http4sDsl[IO] {
//
//  val log = LoggerFactory.getLogger(this.getClass())
////  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
//
//  val routes = HttpRoutes.of[IO] {
//    case GET -> Root / "messages" => {
////      println(repository.getMessages.pull.take(2).toString)
//      Ok(Stream("[") ++ repository.getMessages.map(_.asJson.noSpaces).intersperse(",") ++ Stream("]"), `Content-Type`(MediaType.application.json))
//    }
//
//    case GET -> Root / "messages" / LongVar(id) =>
//      for {
//        getResult <- repository.getMessage(id)
//        response <- messageResult(getResult)
//      } yield response
//
//
//    case req@POST -> Root / "messages" =>
//      for {
//        message <- req.decodeJson[Message]
//        createdMessage <- repository.createMessage(message)
//        response <- Created(createdMessage.asJson, Location(Uri.unsafeFromString(s"/messages/${createdMessage.id.get}")))
//      } yield response
//
//    case req@PUT -> Root / "messages" / LongVar(id) =>
//      for {
//        message <- req.decodeJson[Message]
//        updateResult <- repository.updateMessage(id, message)
//        response <- messageResult(updateResult)
//      } yield response
//
//    case DELETE -> Root / "messages" / LongVar(id) =>
//      repository.deleteMessage(id).flatMap {
//        case Left(MsgNotFoundError) => NotFound()
//        case Right(_) => NoContent()
//      }
//  }
//
//  private def messageResult(result: Either[MsgNotFoundError.type, Message]) = {
//    result match {
//      case Left(MsgNotFoundError) => NotFound()
//      case Right(message) => Ok(message.asJson)
//    }
//  }
//}
