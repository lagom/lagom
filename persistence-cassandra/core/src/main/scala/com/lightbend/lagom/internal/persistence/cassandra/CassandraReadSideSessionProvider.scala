/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import akka.Done
import akka.actor.ExtendedActorSystem
import akka.actor.ActorSystem
import akka.event.Logging
import akka.persistence.cassandra.session.CassandraSessionSettings
import akka.persistence.cassandra.session.scaladsl.{ CassandraSession => AkkaScaladslCassandraSession }
import akka.persistence.cassandra.SessionProvider
import akka.persistence.cassandra.CassandraPluginConfig
import com.datastax.driver.core.Session

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

/**
 * Internal API
 */
private[lagom] object CassandraReadSideSessionProvider {

  def apply(
      system: ActorSystem,
      settings: CassandraSessionSettings,
      executionContext: ExecutionContext
  ): AkkaScaladslCassandraSession = {

    import akka.persistence.cassandra.ListenableFutureConverter
    import akka.util.Helpers.Requiring

    import scala.collection.JavaConverters._ // implicit asScala conversion

    val cfg = settings.config
    val replicationStrategy: String = CassandraPluginConfig.getReplicationStrategy(
      cfg.getString("replication-strategy"),
      cfg.getInt("replication-factor"),
      cfg.getStringList("data-center-replication-factors").asScala
    )

    val keyspaceAutoCreate: Boolean = cfg.getBoolean("keyspace-autocreate")
    val keyspace: String = cfg
      .getString("keyspace")
      .requiring(
        !keyspaceAutoCreate || _ > "",
        "'keyspace' configuration must be defined, or use keyspace-autocreate=off"
      )

    def createKeyspace(ssn: Session, kspc: String, repStrat: String)(
        implicit executionContext: ExecutionContext
    ): Future[Done] = {
      def create(): Future[Done] =
        ssn
          .executeAsync(
            s"""
            CREATE KEYSPACE IF NOT EXISTS $kspc
            WITH REPLICATION = { 'class' : $repStrat }
            """
          )
          .asScala
          .map(_ => Done)

      akka.lagom.internal.SerializedExecutionAccessor.serializedExecution(
        recur = () => createKeyspace(ssn, kspc, repStrat),
        exec = () => create()
      )
    }

    def init(session: Session): Future[Done] = {
      implicit val ec = executionContext
      if (keyspaceAutoCreate) {
        val keyspaceCreationResult: Future[Done] = createKeyspace(session, keyspace, replicationStrategy)
        keyspaceCreationResult
          .flatMap { _ =>
            session.executeAsync(s"USE $keyspace;").asScala
          }
          .map(_ => Done)
      } else if (keyspace != "")
        session.executeAsync(s"USE $keyspace;").asScala.map(_ => Done)
      else
        Future.successful(Done)
    }

    val metricsCategory = "lagom-" + system.name

    // using the scaladsl API because the init function
    new AkkaScaladslCassandraSession(
      system,
      SessionProvider(system.asInstanceOf[ExtendedActorSystem], settings.config),
      settings,
      executionContext,
      Logging.getLogger(system, this.getClass),
      metricsCategory,
      init
    )
  }
}
