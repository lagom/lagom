/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.jdbc

import java.sql.Connection
import java.util.concurrent.TimeUnit
import javax.naming.{ Context, InitialContext }

import akka.Done
import akka.actor.ActorSystem
import akka.persistence.jdbc.config.{ JournalTableConfiguration, SlickConfiguration, SnapshotTableConfiguration }
import akka.persistence.jdbc.journal.dao.JournalTables
import akka.persistence.jdbc.snapshot.dao.SnapshotTables
import akka.persistence.jdbc.util.{ SlickDatabase, SlickDriver }
import akka.util.Timeout
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import play.api.db.DBApi
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.meta.MTable
import slick.jdbc.{ H2Profile, JdbcProfile, MySQLProfile, PostgresProfile }
import slick.util.AsyncExecutor
import akka.persistence.jdbc.util.ConfigOps._
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

private[lagom] class SlickProvider(
  system: ActorSystem,
  dbApi:  DBApi /* Ensures database is initialised before we start anything that needs it */ )(implicit ec: ExecutionContext) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  private val readSideConfig = system.settings.config.getConfig("lagom.persistence.read-side.jdbc")
  private val jdbcConfig = system.settings.config.getConfig("lagom.persistence.jdbc")
  private val createTables = jdbcConfig.getConfig("create-tables")

  private val slickConfig = new SlickConfiguration(readSideConfig)

  val autoCreateTables: Boolean = createTables.getBoolean("auto")

  if (dbApi != null) {

    // the data source as configured by Play
    val dataSource = dbApi.database("default").dataSource

    // the slick db configured with an async executor
    val slickDb =
      SlickDbProvider(
        dataSource,
        system.settings.config.getConfig("jdbc-defaults.slick.async-executor")
      )

    val namingContext = new InitialContext()
    // bind the DB configured with an AsyncExecutor
    namingContext.rebind("DefaultDB", slickDb)

    // for use with JPA only
    namingContext.rebind("DefaultDS", dataSource)
  }

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

    val journalStatements =
      profile match {
        case H2Profile =>
          // Work around https://github.com/slick/slick/issues/763
          journalTables.JournalTable.schema.createStatements
            .map(_.replace("GENERATED BY DEFAULT AS IDENTITY(START WITH 1)", "AUTO_INCREMENT"))
            .toSeq
        case MySQLProfile =>
          // Work around https://github.com/slick/slick/issues/1437
          journalTables.JournalTable.schema.createStatements
            .map(_.replace("AUTO_INCREMENT", "AUTO_INCREMENT UNIQUE"))
            .toSeq
        case _ => journalTables.JournalTable.schema.createStatements.toSeq
      }

    // Work around https://github.com/dnvriend/akka-persistence-jdbc/issues/71
    val snapshotStatements = snapshotTables.SnapshotTable.schema.createStatements.toSeq

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
      currentSchema <- getCurrentSchema
      tables <- getTables(currentSchema)
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
          val stmt = ctx.connection.createStatement()
          try {
            stmt.executeUpdate(s)
          } finally {
            stmt.close()
          }
        }
      }).asTry.flatMap {
        case Success(_) => DBIO.successful(())
        case Failure(f) =>
          getTables(currentSchema).map { tables =>
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

  private def getTables(currentSchema: Option[String]) = {
    // Calling MTable.getTables without parameters fails on MySQL
    // See https://github.com/lagom/lagom/issues/446
    // and https://github.com/slick/slick/issues/1692
    MTable.getTables(None, currentSchema, Option("%"), None)
  }

  private def getCurrentSchema: DBIO[Option[String]] = {
    SimpleDBIO(ctx => tryGetSchema(ctx.connection).getOrElse(null)).flatMap { schema =>
      if (schema == null) {
        // Not all JDBC drivers support the getSchema method:
        // some always return null.
        // In that case, fall back to vendor-specific queries.
        profile match {
          case _: H2Profile =>
            sql"SELECT SCHEMA();".as[String].headOption
          case _: MySQLProfile =>
            sql"SELECT DATABASE();".as[String].headOption
          case _: PostgresProfile =>
            sql"SELECT current_schema();".as[String].headOption
          case _ =>
            DBIO.successful(None)
        }
      } else DBIO.successful(Some(schema))
    }
  }

  // Some older JDBC drivers don't implement Connection.getSchema
  // (including some builds of H2). This causes them to throw an
  // AbstractMethodError at runtime.
  // Because Try$.apply only catches NonFatal errors, and AbstractMethodError
  // is considered fatal, we need to construct the Try explicitly.
  private def tryGetSchema(connection: Connection): Try[String] =
    try Success(connection.getSchema) catch {
      case e: AbstractMethodError => Failure(e)
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
