/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.jdbc

import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.ActorSystem
import akka.persistence.jdbc.config.{ JournalTableConfiguration, SlickConfiguration, SnapshotTableConfiguration }
import akka.persistence.jdbc.dao.bytea.journal.JournalTables
import akka.persistence.jdbc.dao.bytea.snapshot.SnapshotTables
import akka.persistence.jdbc.util.{ SlickDatabase, SlickDriver }
import akka.util.Timeout
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import org.slf4j.LoggerFactory
import play.api.db.DBApi
import slick.ast._
import slick.driver.{ H2Driver, JdbcProfile, MySQLDriver, PostgresDriver }
import slick.jdbc.meta.MTable

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

private[lagom] class SlickProvider(
  system: ActorSystem,
  dbApi:  DBApi /* Ensures database is initialised before we start anything that needs it */ )(implicit ec: ExecutionContext) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  private val readSideConfig = system.settings.config.getConfig("lagom.persistence.read-side.jdbc")
  private val jdbcConfig = system.settings.config.getConfig("lagom.persistence.jdbc")
  private val createTables = jdbcConfig.getConfig("create-tables")

  private val slickConfig = new SlickConfiguration(readSideConfig)

  val autoCreateTables: Boolean = createTables.getBoolean("auto")

  val db = SlickDatabase.forConfig(readSideConfig, slickConfig)
  val profile = SlickDriver.forDriverName(readSideConfig)

  import profile.api._

  private val createTablesTimeout = createTables.getDuration("timeout", TimeUnit.MILLISECONDS).millis

  // This feature is somewhat limited, it assumes that the read side database is the same database as the journals and
  // snapshots
  private val createTablesTask: Option[ClusterStartupTask] = if (autoCreateTables) {
    val journalCfg = new JournalTableConfiguration(system.settings.config.getConfig("jdbc-read-journal"))
    val snapshotCfg = new SnapshotTableConfiguration(system.settings.config.getConfig("jdbc-snapshot-store"))

    val journalTables = new JournalTables {
      override val journalTableCfg: JournalTableConfiguration = journalCfg
      override val profile: JdbcProfile = SlickProvider.this.profile
    }

    val snapshotTables = new SnapshotTables {
      override val snapshotTableCfg: SnapshotTableConfiguration = snapshotCfg
      override val profile: JdbcProfile = SlickProvider.this.profile
    }

    val journalStatements = {
      val s = profile match {
        case H2Driver =>
          // Work around https://github.com/slick/slick/issues/763
          journalTables.JournalTable.schema.createStatements
            .map(_.replace("GENERATED BY DEFAULT AS IDENTITY(START WITH 1)", "AUTO_INCREMENT"))
            .toSeq
        case _ => journalTables.JournalTable.schema.createStatements.toSeq
      }

      // Work around https://github.com/dnvriend/akka-persistence-jdbc/issues/73
      // The work around below is very specific to how Slick generates the schema, and so is very fragile, but it
      // will do for now.
      s.map(_.replace(" NOT NULL)", ")"))
    }

    // Work around https://github.com/dnvriend/akka-persistence-jdbc/issues/71
    val snapshotStatements = snapshotTables.SnapshotTable.schema.createStatements
      .map(_.replace(" PRIMARY KEY,", ",")).toSeq

    snapshotTables.SnapshotTable.schema.create

    val conf = jdbcConfig.getConfig("create-tables")
    val minBackoff = conf.getDuration("failure-exponential-backoff.min", TimeUnit.MILLISECONDS).millis
    val maxBackoff = conf.getDuration("failure-exponential-backoff.max", TimeUnit.MILLISECONDS).millis
    val randomBackoffFactor = conf.getDouble("failure-exponential-backoff.random-factor")
    val role = conf.getString("run-on-role") match {
      case "" => None
      case r  => Some(r)
    }

    def createTables() = {
      db.run {
        for {
          _ <- createTable(journalStatements, tableExists(journalCfg.schemaName, journalCfg.tableName))
          _ <- createTable(snapshotStatements, tableExists(snapshotCfg.schemaName, snapshotCfg.tableName))
        } yield Done.getInstance()
      }
    }

    val task = ClusterStartupTask(
      system, "jdbcCreateTables", createTables, createTablesTimeout, role, minBackoff, maxBackoff, randomBackoffFactor
    )
    Some(task)
  } else {
    None
  }

  /**
   * Attempt to create the table. If creation is unsuccessful, it checks whether the table was created anyway
   * (perhaps by another node), and if it was, assumes that was the reason it was unsuccessful, and returns success.
   */
  def createTable(schemaStatements: Seq[String], tableExists: (Vector[MTable], Option[String]) => Boolean) = {
    for {
      tables <- MTable.getTables
      currentSchema <- getCurrentSchema
      _ <- createTableInternal(tables, currentSchema, schemaStatements, tableExists)
    } yield Done.getInstance()
  }

  private def createTableInternal(tables: Vector[MTable], currentSchema: Option[String],
                                  schemaStatements: Seq[String], tableExists: (Vector[MTable], Option[String]) => Boolean) = {

    if (tableExists(tables, currentSchema)) {
      DBIO.successful(())
    } else {
      if (logger.isDebugEnabled) {
        logger.debug("Creating table, executing: " + schemaStatements.mkString("; "))
      }

      DBIO.sequence(schemaStatements.map { s =>
        SimpleDBIO { ctx =>
          ctx.connection.prepareCall(s).execute()
        }
      }).asTry.flatMap {
        case Success(_) => DBIO.successful(())
        case Failure(f) =>
          MTable.getTables.map { tables =>
            if (tableExists(tables, currentSchema)) {
              logger.debug("Table creation failed, but table existed after it was created, ignoring failure", f)
              ()
            } else {
              throw f
            }
          }
      }
    }
  }

  private def getCurrentSchema: DBIO[Option[String]] = {
    profile match {
      case _: H2Driver =>
        sql"SELECT SQL FROM INFORMATION_SCHEMA.SESSION_STATE WHERE KEY='SCHEMA_SEARCH_PATH';".as[String]
          .headOption.map(_.orElse(Some("PUBLIC")))
      case _: PostgresDriver =>
        sql"SELECT current_schema();".as[String].headOption
      case _ =>
        DBIO.successful(None)
    }
  }

  def tableExists(schemaName: Option[String], tableName: String)(tables: Vector[MTable], currentSchema: Option[String]): Boolean = {
    tables.exists { t =>
      t.name.schema.orElse(currentSchema) == schemaName.orElse(currentSchema) && t.name.name == tableName
    }
  }

  /**
   * Ensure the tables are created if configured.
   */
  def ensureTablesCreated(): Future[Done] = {
    createTablesTask match {
      case None =>
        Future.successful(Done.getInstance())
      case Some(task) =>
        implicit val timeout = Timeout(createTablesTimeout)
        task.askExecute()
    }
  }
}
