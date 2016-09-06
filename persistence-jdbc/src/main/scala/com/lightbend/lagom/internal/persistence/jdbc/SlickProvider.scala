package com.lightbend.lagom.internal.persistence.jdbc

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

import akka.Done
import akka.actor.ActorSystem
import akka.persistence.jdbc.config.{JournalTableConfiguration, SlickConfiguration, SnapshotTableConfiguration}
import akka.persistence.jdbc.dao.bytea.journal.JournalTables
import akka.persistence.jdbc.dao.bytea.snapshot.SnapshotTables
import akka.persistence.jdbc.util.{SlickDatabase, SlickDriver}
import akka.util.Timeout
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import org.slf4j.LoggerFactory
import play.api.Configuration
import slick.driver.JdbcProfile
import slick.jdbc.meta.MTable

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

@Singleton
class SlickProvider @Inject() (system: ActorSystem, configuration: Configuration) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  private val config = configuration.underlying
  private val readSideConfig = config.getConfig("lagom.persistence.read-side.jdbc")
  private val jdbcConfig = config.getConfig("lagom.persistence.jdbc")

  private val slickConfig = new SlickConfiguration(readSideConfig)

  val autoCreateTables = jdbcConfig.getBoolean("autocreate-tables")

  val db = SlickDatabase.forConfig(readSideConfig, slickConfig)
  val profile = SlickDriver.forDriverName(readSideConfig)

  import profile.api._

  private val createTablesTimeout =  jdbcConfig.getDuration("create-tables.timeout", TimeUnit.MILLISECONDS).millis


  // This feature is somewhat limited, it assumes that the read side database is the same database as the journals and
  // snapshots
  private val createTablesTask: Option[ClusterStartupTask] = if (autoCreateTables) {
    val journalCfg = new JournalTableConfiguration(config.getConfig("jdbc-read-journal"))
    val snapshotCfg = new SnapshotTableConfiguration(config.getConfig("jdbc-snapshot-store"))

    val journalTables = new JournalTables {
      override val journalTableCfg: JournalTableConfiguration = journalCfg
      override val profile: JdbcProfile = SlickProvider.this.profile
    }

    val snapshotTables = new SnapshotTables {
      override val snapshotTableCfg: SnapshotTableConfiguration = snapshotCfg
      override val profile: JdbcProfile = SlickProvider.this.profile
    }

    snapshotTables.SnapshotTable.schema.create

    val conf = jdbcConfig.getConfig("create-tables")
    val minBackoff = conf.getDuration("failure-exponential-backoff.min", TimeUnit.MILLISECONDS).millis
    val maxBackoff = conf.getDuration("failure-exponential-backoff.max", TimeUnit.MILLISECONDS).millis
    val randomBackoffFactor = conf.getDouble("failure-exponential-backoff.random-factor")
    val role = conf.getString("run-on-role") match {
      case "" => None
      case r => Some(r)
    }

    def createTables() = {
      db.run {
        for {
          _ <- createTable(journalTables.JournalTable.schema.create, tableExists(journalCfg.schemaName, journalCfg.tableName))
          _ <- createTable(snapshotTables.SnapshotTable.schema.create, tableExists(snapshotCfg.schemaName, snapshotCfg.tableName))
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
  def createTable(createSchema: DBIOAction[_, NoStream, Effect.Schema], tableExists: Vector[MTable] => Boolean) = {
    MTable.getTables.flatMap { tables =>
      if (tableExists(tables)) {
        DBIO.successful(())
      } else {
        createSchema.asTry.flatMap {
          case Success(_) => DBIO.successful(())
          case Failure(f) =>
            MTable.getTables.map { tables =>
              if (tableExists(tables)) {
                logger.debug("Table creation failed, but table existed after it was created, ignoring failure", f)
                ()
              } else {
                throw f
              }
            }
        }
      }
    }
  }

  def tableExists(schemaName: Option[String], tableName: String)(tables: Vector[MTable]): Boolean = {
    tables.exists(t => t.name.schema == schemaName && t.name.name == tableName)
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