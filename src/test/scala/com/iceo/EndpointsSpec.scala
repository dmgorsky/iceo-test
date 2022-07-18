package com.iceo
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{UriContext, basicRequest}
import sttp.tapir.server.stub.TapirStubInterpreter
import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import com.iceo.Main.{Resources, getMessagesEndpoint, resources}
import com.iceo.config._
import com.iceo.db.Database
import com.iceo.model.Message
import com.iceo.repository.MessageRepository
import doobie.Transactor
import doobie.util.ExecutionContexts
import io.circe.generic.auto._
import sttp.client3.circe._
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.http4s.Http4sServerInterpreter

class EndpointsSpec extends AnyFlatSpec with Matchers with EitherValues with Endpoints[IO] {

  it should "return messages response" in {
    val transactor = Transactor.fromDriverManager[IO]("org.postgresql.Driver", "jdbc:postgresql://localhost:5432/iceo", "docker", "docker")
    val repository = new MessageRepository(transactor)
    val logics = new EndpointsLogics[IO](repository)
    val backendStub = TapirStubInterpreter(SttpBackendStub(new CatsMonadError[IO]()))
      .whenServerEndpoint(getMessagesEndpointList.serverLogic(logics.getMessagesLogicList))
//      .whenServerEndpoint(getMessagesEndpoint.serverLogic(logics.getMessagesLogic))
      .thenRunLogic()
      .backend()

    val response = basicRequest
      .get(uri"http://test.com/messages")
      .response(asJson[List[Message]])
      .send(backendStub)
      .unsafeRunSync()

    response.body.value shouldNot be(empty)
    response.body.value should contain(Message(Some(1), "Msg #1"))

  }

//  implicit class Unwrapper[T](t: IO[T]) {
//    def unwrap: T = t.unsafeRunSync()
//  }
}
