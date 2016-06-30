package com.lightbend.lagom.javadsl.persistence.cassandra

import java.util.concurrent.TimeUnit

import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.slf4j.LoggerFactory

import com.google.inject.Inject

import akka.Done
import akka.actor.ActorSystem
import akka.actor.Scheduler
import akka.pattern.after
import play.api.Configuration

class CassandraReadSideTableCreator @Inject() (db: CassandraSession, system: ActorSystem, config: CassandraReadSideTableCreator.Config)(implicit ec: ExecutionContext) {
  private val log = LoggerFactory.getLogger(classOf[CassandraReadSideTableCreator])

  private implicit val scheduler = system.scheduler

  def createTable(createTableStatement: String): Future[Done] = {
    val createTable = retry(config.retries, config.retryBackoff) {
      db.executeCreateTable(createTableStatement).toScala
    }.map(_ => Done)

    createTable.onFailure {
      case err: Throwable => log.error(s"Failed to execute create table statement:\n$createTableStatement. Reason: ${err.getMessage}", err)
    }

    createTable
  }

  private def retry[T](retries: Int, delay: FiniteDuration)(op: => Future[T])(implicit ec: ExecutionContext, s: Scheduler): Future[T] = {
    op.recoverWith {
      case _ if (retries != 0) =>
        val retriesLeft = if (retries > 0) retries - 1 else retries
        after(delay, s)(retry(retriesLeft, delay)(op)(ec, s))
    }
  }
}

object CassandraReadSideTableCreator {
  private class Config @Inject() (config: Configuration) {
    val retries: Int = config.getInt("lagom.persistence.read-side.cassandra.create-table.retries").get
    val retryBackoff: FiniteDuration = {
      val backoff = config.getMilliseconds("lagom.persistence.read-side.cassandra.create-table.retry-backoff-interval").get
      new FiniteDuration(backoff, TimeUnit.MILLISECONDS)
    }
  }
}
