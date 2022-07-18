val projectScalaVersion = "2.13.8" // "3.1.2-RC1" // "2.13.8"

val tapirVersion = "1.0.1"
val DoobieVersion = "1.0.0-RC2"
val PostgresVersion = "42.4.0"
val FlywayVersion = "8.5.13"
val PureConfigVersion = "0.17.1"

val doobieCore = "org.tpolecat" %% "doobie-core" % DoobieVersion
val doobiePostgres = "org.tpolecat" %% "doobie-postgres" % DoobieVersion
val doobiePostgresCirce = "org.tpolecat" %% "doobie-postgres-circe" % DoobieVersion
val doobieHikari = "org.tpolecat" %% "doobie-hikari" % DoobieVersion
val postgresql = "org.postgresql" % "postgresql" % PostgresVersion
val flyway = "org.flywaydb" % "flyway-core" % FlywayVersion
val pureConfig = "com.github.pureconfig" %% "pureconfig" % PureConfigVersion
val pureConfigCats = "com.github.pureconfig" %% "pureconfig-cats-effect" % PureConfigVersion

lazy val rootProject = (project in file("."))
  .settings(
    Seq(
      name := "iceo-test-dhorskyi",
      version := "0.1.0-SNAPSHOT",
      organization := "com.iceo",
      scalaVersion := projectScalaVersion,
      libraryDependencies ++= Seq(
        "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion,
        "org.http4s" %% "http4s-blaze-server" % "0.23.12",
        "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
        "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
        "ch.qos.logback" % "logback-classic" % "1.2.11",
        "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % tapirVersion % Test,
        "org.scalatest" %% "scalatest" % "3.2.12" % Test,
        "com.softwaremill.sttp.client3" %% "circe" % "3.6.2" % Test,
        "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.0.1",
        doobieCore,
        doobiePostgres,
        doobiePostgresCirce,
        doobieHikari,
        postgresql,
        flyway,
        pureConfig,
        pureConfigCats
      ),
      addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
    ),
    test / fork := true
  )
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings(
    Docker / version := "latest",
    Docker / packageName := "iceo-test-dhorskyi",
    dockerExposedPorts := Seq(8082)
  )

