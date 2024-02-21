### Requirements:
* API gateway with pagination and ability to indicate sorting. Searching returns sorted, paginated results
* API gateway with ability to search data using optional parameter (p.e flag) and list of ids
* API documentation prepared (swagger)
* application configuration via configuration file
* test examples
* application Dockerization
* easy way to install and run application - docker compose with database population

### Solution

Techs:
* Doobie, FS2, tapir @ http4s, circe, pureconfig
* Postgres, flyway, hikaricp

`http://localhost:8080/messages/from=1&take=10&sortIdsBy=DESC&messagesLike=#2&idsList=1,3,5,7`


Endpoint: 1 for all req`s, lazy variant 
```scala
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
```
EP Logic:
```scala
  def getMessagesLogic(fromToOrder: PagingSortingParams): F[Either[Unit, Stream[IO, Byte]]] = {
    val (from, take, sort, msgLike, idsList) =
      PagingSortingParams.unapply(fromToOrder).get match {
        case (from, take, sort, msgLike, idsList) =>
          (from, take, sort, msgLike, idsList.map(_.split(",").map(_.toLong)))
      }

    val s = Stream("[") ++ repository.getMessages(from, take, sort, msgLike, idsList).map(_.asJson.noSpaces).intersperse(",") ++ Stream("]")
    val bs = s.through(fs2.text.utf8.encode)
    val ret: Either[Unit, Stream[IO, Byte]] = Right(bs)
    ret.pure[F]
  }
```
Here 'sortIdsBy' is demonstrating sorting just by `id`, options are ASC or DESC.

Doobie's repo code for this (filters combined in `whereAndOpt`):
```scala
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

```
Here filters 'start from' and 'how many to take' implemented in fs2 stream's 'drop' and 'take'.

Endpoint described for swagger in tapir:
```scala
val docs: List[AnyEndpoint] = List(getMessagesEndpoint)
val swagger = SwaggerInterpreter().fromEndpoints[IO](docs, "iceo-test", "1.0.0")
val swaggerRoute = Http4sServerInterpreter[IO]().toRoutes(swagger)
```

Docker for running postgres:
```dockerfile
services:
  db:
    image: postgres:14-alpine
    restart: always
    volumes:
      - "./sql:/docker-entrypoint-initdb.d"
    environment:
      - POSTGRES_USER=docker
      - POSTGRES_PASSWORD=docker
      - POSTGRES_DB=iceo
#      - POSTGRES_HOST_AUTH_METHOD=trust
      - DEBUG
    ports:
      - 5432:5432
  adminer:
    image: adminer
    restart: always
    ports:
      - 8080:8080
```

Initial table created/populated in flyway migration
```sql
CREATE TABLE messages (
  id SERIAL PRIMARY KEY,
  message TEXT
);
```

App dockerization is possible via `sbt-native-packager`, but docker-compose does not use local-published image, so
```shell
docker-compose up
sbt run
```

I didn't invest too much time in testing (shame on me), just in demo purposes
```scala
val transactor = Transactor.fromDriverManager[IO]("org.postgresql.Driver", "jdbc:postgresql://localhost:5432/iceo", "docker", "docker")
val repository = new MessageRepository(transactor)
val logics = new EndpointsLogics[IO](repository)
val backendStub = TapirStubInterpreter(SttpBackendStub(new CatsMonadError[IO]()))
  .whenServerEndpoint(getMessagesEndpointList.serverLogic(logics.getMessagesLogicList))
  .thenRunLogic()
  .backend()
val response = basicRequest
  .get(uri"http://test.com/messages")
  .response(asJson[List[Message]])
  .send(backendStub)
  .unsafeRunSync()

response.body.value shouldNot be(empty)
response.body.value should contain(Message(Some(1), "Msg #1"))
```

