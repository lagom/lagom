/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import scala.concurrent.Future
import java.net.URI

import akka.actor.ActorSystem
import akka.event.{ Logging, LoggingAdapter }
import com.lightbend.lagom.internal.scaladsl.persistence.cassandra.{ CassandraPersistentEntityRegistry, CassandraReadSideImpl, ScaladslCassandraOffsetStore }
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.persistence.{ PersistenceComponents, PersistentEntityRegistry, ReadSidePersistenceComponents, WriteSidePersistenceComponents }
import com.lightbend.lagom.internal.persistence.cassandra.{ CassandraConsistencyValidator, CassandraOffsetStore, CassandraReadSideSettings, ServiceLocatorAdapter, ServiceLocatorHolder }
import com.lightbend.lagom.spi.persistence.OffsetStore
import org.slf4j.LoggerFactory
import play.api.{ Configuration, Environment, Mode }

trait Validating {

  def environment: Environment

  def actorSystem: ActorSystem

  //  private val logger = LoggerFactory.getLogger(getClass)
  val loggingAdapter: LoggingAdapter = Logging(actorSystem, this.getClass)

  protected def validate(errors: Seq[String]): Unit = {
    if (errors.nonEmpty) {
      // TODO: improve details wrt why it's not valid.
      val failureMsg = s"Your Cassandra setup is not valid for Production. Errors: ${errors.mkString("[", ", ", "]")}"
      if (environment.mode == Mode.Prod) {
        throw new IllegalArgumentException(failureMsg)
      } else {
        loggingAdapter.warning("loggingAdapte: " + failureMsg)
        //        logger.warn("logger: " + failureMsg)
      }
    }
  }

}

/**
 * Persistence Cassandra components (for compile-time injection).
 */
trait CassandraPersistenceComponents extends PersistenceComponents
  with ReadSideCassandraPersistenceComponents
  with WriteSideCassandraPersistenceComponents

/**
 * Write-side persistence Cassandra components (for compile-time injection).
 */
trait WriteSideCassandraPersistenceComponents extends WriteSidePersistenceComponents with Validating {
  override lazy val persistentEntityRegistry: PersistentEntityRegistry =
    new CassandraPersistentEntityRegistry(actorSystem)

  def serviceLocator: ServiceLocator

  def configuration: Configuration

  validate(CassandraConsistencyValidator.validateWriteSide(configuration))

  // eager initialization
  private[lagom] val serviceLocatorHolder: ServiceLocatorHolder = {
    val holder = ServiceLocatorHolder(actorSystem)
    holder.setServiceLocator(new ServiceLocatorAdapter {
      override def locate(name: String): Future[Option[URI]] = serviceLocator.locate(name)
    })
    holder
  }

}

/**
 * Read-side persistence Cassandra components (for compile-time injection).
 */
trait ReadSideCassandraPersistenceComponents extends ReadSidePersistenceComponents with Validating {

  def configuration: Configuration

  def environment: Environment

  validate(CassandraConsistencyValidator.validateReadSide(configuration))

  lazy val cassandraSession: CassandraSession = new CassandraSession(actorSystem)
  lazy val testCasReadSideSettings: CassandraReadSideSettings = new CassandraReadSideSettings(actorSystem)

  private[lagom] lazy val cassandraOffsetStore: CassandraOffsetStore =
    new ScaladslCassandraOffsetStore(actorSystem, cassandraSession, testCasReadSideSettings, readSideConfig)(executionContext)
  lazy val offsetStore: OffsetStore = cassandraOffsetStore

  lazy val cassandraReadSide: CassandraReadSide = new CassandraReadSideImpl(actorSystem, cassandraSession, cassandraOffsetStore)
}
