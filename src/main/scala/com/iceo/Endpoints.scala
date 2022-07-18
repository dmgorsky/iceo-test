package com.iceo

import cats.Applicative
import scala.util.{Either, Right, Left}
import cats.effect.IO
//import cats.syntax.either._
import cats.syntax.ApplicativeSyntax
import com.iceo.model.Message
import com.iceo.repository.MessageRepository
import fs2.Stream
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.{Charset, MediaType, Uri}
import org.http4s.headers.`Content-Type`
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir
import tapir.json.circe._

import java.util.concurrent.atomic.AtomicReference
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import cats.Applicative
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._
import io.circe.generic.auto._
import tapir.json.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import fs2.Stream
import io.circe.{Decoder, Encoder}
import org.http4s.headers.{Location, `Content-Type`}
import sttp.tapir.EndpointOutput.StatusCode
import sttp.tapir._
import sttp.tapir.CodecFormat.TextPlain
import sttp.capabilities.fs2.Fs2Streams

trait Endpoints[F[_]] {
  val paging: EndpointInput[PagingSortingParams] =
    query[Option[Long]]("from")
      .and(query[Option[Long]]("take"))
      .and(query[Option[String]]("sortIdsBy"))
      .and(query[Option[String]]("messagesLike"))
      .and(query[Option[String]]("idsList"))
      .mapTo[PagingSortingParams]

  val getMessagesEndpoint: PublicEndpoint[PagingSortingParams, Unit, Stream[F, Byte], Fs2Streams[F]] = endpoint.get
    .name("getMessages")
    .in("messages")
    .in(paging)
    .out(streamBody(Fs2Streams[F])(Schema.derived[Message], CodecFormat.Json()))
    .description("Get all messages from DB")

  val getMessagesEndpointList: PublicEndpoint[PagingSortingParams, Unit, List[Message], Unit] = endpoint.get
    // List version
    .name("getMessages")
    .in("messages")
    .in(paging)
    .out(jsonBody[List[Message]])
//    .out(stringBodyUtf8AnyFormat(Codec.string))

}

case class PagingSortingParams(
    from: Option[Long],
    take: Option[Long],
    orderIds: Option[String],
    messagesLike: Option[String],
    idsList: Option[String]
)

class EndpointsLogics[F[_]: Applicative](repository: MessageRepository) {

  def getMessagesLogic(fromToOrder: PagingSortingParams): F[Either[Unit, Stream[IO, Byte]]] = {
    val (from, take, sort, msgLike, idsList) =
      PagingSortingParams.unapply(fromToOrder).get match {
        case (from, take, sort, msgLike, idsList) =>
          (from, take, sort, msgLike, idsList.map(_.split(",").map(_.toLong)))
      }

//    val msgs = repository.getMessages(from, take, sort, msgLike, idsList).compile.toList.unsafeRunSync()
    val s = Stream("[") ++ repository.getMessages(from, take, sort, msgLike, idsList).map(_.asJson.noSpaces).intersperse(",") ++ Stream("]")
    val bs = s.through(fs2.text.utf8.encode)
    val ret: Either[Unit, Stream[IO, Byte]] = Right(bs)

    ret.pure[F]
  }

  def getMessagesLogicList(fromToOrder: PagingSortingParams): F[Either[Unit, List[Message]]] = {
    // List version
    val (from, take, sort, msgLike, idsList) =
      PagingSortingParams.unapply(fromToOrder).get match {
        case (from, take, sort, msgLike, idsList) =>
          (from, take, sort, msgLike, idsList.map(_.split(",").map(_.toLong)))
      }
    val s = repository.getMessages(from, take, sort, msgLike, idsList).compile.toList.unsafeRunSync()
    s.asRight[Unit].pure[F]
  }

}
