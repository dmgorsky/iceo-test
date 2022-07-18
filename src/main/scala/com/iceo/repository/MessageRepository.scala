package com.iceo.repository

import cats.data.NonEmptyList
import cats.effect.IO
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import fs2.Stream
import com.iceo.model.{Message, MsgNotFoundError}
import org.slf4j.LoggerFactory

class MessageRepository(transactor: Transactor[IO]) {

  val log = LoggerFactory.getLogger(this.getClass)

  def getMessages(from: Option[Long], take: Option[Long], sort: Option[String], msgLike: Option[String], idsList: Option[Array[Long]]): Stream[IO, Message] = {
    log.info(s"getMessages from $from take $take order ids $sort msg like $msgLike idsList: ")

    val maybeLikePattern = msgLike // todo dumb validation
      .map(p => if (p.contains('%')) p else "%" + p + "%")
      .map(p => if (p.contains('\'')) p else "'" + p + "'")

    val maybeIds = idsList.flatMap(ids => NonEmptyList.fromList(ids.toList))


    val maybeFilterFragment = maybeLikePattern.map(pattern => fr"message LIKE " ++ Fragment.const(pattern))
    val maybeInFragment = maybeIds.map(ids => Fragments.in(fr"id", ids))
    val whereFragment = Fragments.whereAndOpt(maybeFilterFragment, maybeInFragment)

    val maybeSortFragment = sort.fold(fr"")(sortOrder => fr" ORDER BY id " ++ Fragment.const(sortOrder))

    log.info((sql"SELECT id, message FROM messages " ++ whereFragment ++ maybeSortFragment).toString())
    val initial = (sql"SELECT id, message FROM messages "
      ++ whereFragment ++ maybeSortFragment)
      .query[Message]
      .stream
      .transact(transactor)
      .drop(from.getOrElse(1L) - 1) // start from 0
    val appliedTake = take match {
      case Some(num) => initial.take(num)
      case None      => initial
    }
    appliedTake
  }

  def getMessage(id: Long): IO[Either[MsgNotFoundError.type, Message]] = {
    sql"SELECT id, message FROM messages WHERE id = $id".query[Message].option.transact(transactor).map {
      case Some(message) => Right(message)
      case None          => Left(MsgNotFoundError)
    }
  }

  def createMessage(message: Message): IO[Message] = {
    sql"INSERT INTO messages (message) VALUES (${message.message})".update.withUniqueGeneratedKeys[Long]("id").transact(transactor).map {
      id =>
        message.copy(id = Some(id))
    }
  }

  def deleteMessage(id: Long): IO[Either[MsgNotFoundError.type, Unit]] = {
    sql"DELETE FROM messages WHERE id = $id".update.run.transact(transactor).map { affectedRows =>
      if (affectedRows == 1) {
        Right(())
      } else {
        Left(MsgNotFoundError)
      }
    }
  }

  def updateMessage(id: Long, message: Message): IO[Either[MsgNotFoundError.type, Message]] = {
    sql"UPDATE messages SET message = ${message.message} WHERE id = $id".update.run.transact(transactor).map { affectedRows =>
      if (affectedRows == 1) {
        Right(message.copy(id = Some(id)))
      } else {
        Left(MsgNotFoundError)
      }
    }
  }
}
