package com.iceo.db

import cats.effect.{IO, Resource}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import com.iceo.config.DatabaseConfig
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import org.flywaydb.core.Flyway

import scala.concurrent.ExecutionContext

object Database {
  def transactor(dbConfig: DatabaseConfig, executionContext: ExecutionContext): Resource[IO, HikariTransactor[IO]] = {
    HikariTransactor.newHikariTransactor[IO](
      dbConfig.driver,
      dbConfig.url,
      dbConfig.user,
      dbConfig.password,
      executionContext
    )
  }

  def bootstrap(xa: Transactor[IO]): IO[Int] = {
    //    MessageService.createTable.run.transact(xa)
    val nop = 42
    IO.pure(nop)
  }

  def initialize(transactor: HikariTransactor[IO]): IO[Unit] = {
    transactor.configure { dataSource =>
      IO {
        val flyWay = Flyway.configure().dataSource(dataSource)
          .baselineOnMigrate(true) // todo maybe application.conf
          .load()
        flyWay.migrate()
        ()
      }
    }
  }
}
