/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.persistence

import java.net.URLEncoder

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.internal.projection.ProjectionRegistry
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.scaladsl.persistence._

import scala.concurrent.ExecutionContext

private[lagom] class ReadSideImpl(
    system: ActorSystem,
    config: ReadSideConfig,
    persistentEntityRegistry: PersistentEntityRegistry,
    projectionRegistryImpl: ProjectionRegistry,
    name: Option[String]
)(implicit ec: ExecutionContext, mat: Materializer)
    extends ReadSide {

  override def register[Event <: AggregateEvent[Event]](processorFactory: => ReadSideProcessor[Event]): Unit =
    registerFactory(() => processorFactory)

  private[lagom] def registerFactory[Event <: AggregateEvent[Event]](
      processorFactory: () => ReadSideProcessor[Event]
  ) = {

    val readSideProcessor = processorFactory()
    val readSideName      = name.fold("")(_ + "-") + readSideProcessor.readSideName
    val tags              = readSideProcessor.aggregateTags
    val entityIds         = tags.map(_.tag)
    // try to create one instance to fail fast
    val eventClass = tags.headOption match {
      case Some(tag) => tag.eventType
      case None =>
        throw new IllegalArgumentException(s"ReadSideProcessor ${readSideProcessor.getClass.getName} returned 0 tags")
    }

    val encodedReadSideName = URLEncoder.encode(readSideName, "utf-8")
    val globalPrepareTask: ClusterStartupTask =
      ClusterStartupTask(
        system,
        s"readSideGlobalPrepare-$encodedReadSideName",
        () => processorFactory().buildHandler().globalPrepare(),
        config.globalPrepareTimeout,
        config.role,
        config.minBackoff,
        config.maxBackoff,
        config.randomBackoffFactor
      )

    val streamName     = tags.head.eventType.getName
    val projectionName = readSideName

    val readSideProps = (projectionRegistryActorRef: ActorRef) =>
      ReadSideActor.props(
        streamName,
        projectionName,
        config,
        eventClass,
        globalPrepareTask,
        persistentEntityRegistry.eventStream[Event],
        processorFactory,
        projectionRegistryActorRef
      )

    projectionRegistryImpl.registerProjectionGroup(
      streamName, // TODO: use the name from the entity, not the tags
      entityIds,
      readSideName,
      config.role,
      readSideProps
    )

  }
}
