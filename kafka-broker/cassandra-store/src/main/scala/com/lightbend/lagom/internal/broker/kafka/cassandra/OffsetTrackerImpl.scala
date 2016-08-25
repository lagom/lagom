/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka.cassandra

import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.{ Failure, Success }

import org.slf4j.LoggerFactory

import com.datastax.driver.core.PreparedStatement
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId
import com.lightbend.lagom.javadsl.persistence.Offset
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession

import akka.Done
import javax.inject.Inject
import javax.inject.Singleton
import com.lightbend.lagom.internal.persistence.cassandra.OffsetStore
import com.lightbend.lagom.internal.broker.kafka.store.OffsetTracker
import com.typesafe.config.Config

@Singleton
class OffsetTrackerImpl @Inject() (config: OffsetTrackerImpl.OffsetTableConfiguration, info: ServiceInfo, db: CassandraSession)(implicit ec: ExecutionContext) extends OffsetTracker {

  private val log = LoggerFactory.getLogger(classOf[OffsetTracker])

  private val offsetStore = OffsetStore(db, config.tableName)

  override def of(topicId: TopicId): Future[OffsetTracker.OffsetDao] = {
    for {
      _ <- offsetStore.globalPrepare()
      tablePrimaryKey = tablePrimaryKeyFor(topicId)
      lastOffset <- offsetStore.prepare(tablePrimaryKey)
    } yield {
      new OffsetTrackerImpl.OffsetDao(db, offsetStore, tablePrimaryKey, lastOffset)
    }
  }

  private def tablePrimaryKeyFor(topicId: TopicId): String = {
    val configPrimayKeyPrefix = config.primaryKeyPrefix.trim
    val prefix =
      if (configPrimayKeyPrefix.isEmpty) info.serviceName
      else configPrimayKeyPrefix

    s"$prefix-${topicId.value}".trim
  }
}

object OffsetTrackerImpl {
  class OffsetDao private[OffsetTrackerImpl] (db: CassandraSession, store: OffsetStore, tablePrimaryKey: String, val lastOffset: Offset) extends OffsetTracker.OffsetDao {
    override def save(offset: Offset): Future[Done] = {
      val boundStatement = store.writeOffset(tablePrimaryKey, offset)
      db.executeWrite(boundStatement).toScala
    }
  }

  trait OffsetTableConfiguration {
    def tableName: String
    def primaryKeyPrefix: String
  }

  object OffsetTableConfiguration {
    def apply(conf: Config): OffsetTableConfiguration =
      new OffsetTableConfigurationImpl(conf.getConfig("lagom.broker.kafka.offset-store"))

    private class OffsetTableConfigurationImpl(conf: Config) extends OffsetTableConfiguration {
      override def tableName: String = conf.getString("table-name")
      override def primaryKeyPrefix: String = conf.getString("primary-key-prefix")
    }
  }
}
