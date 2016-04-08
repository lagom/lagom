/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.internal.persistence

import com.lightbend.lagom.persistence.CorePersistentEntityRegistry
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.javadsl.persistence.PersistentEntity
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef
import com.lightbend.lagom.persistence.AggregateEvent
import com.lightbend.lagom.persistence.AggregateEventTag
import java.util.Optional
import java.util.UUID
import akka.stream.javadsl
import akka.NotUsed
import akka.japi.Pair
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.CompletionStage
import akka.Done
import scala.compat.java8.FutureConverters._
import javax.inject.Inject
import com.google.inject.Injector
import scala.reflect.ClassTag

class PersistentEntityRegistryDelegate @Inject() (persistentEntityRegistry: CorePersistentEntityRegistry, injector: Injector) extends PersistentEntityRegistry {
  def register[C, E, S, Entity <: PersistentEntity[C, E, S]](entityClass: Class[Entity]): Unit =
    persistentEntityRegistry.register[C, E, S, Entity](() => injector.getInstance(entityClass))(ClassTag(entityClass))

  def refFor[C](entityClass: Class[_ <: PersistentEntity[C, _, _]], entityId: String): PersistentEntityRef[C] =
    new PersistentEntityRefDelegate(persistentEntityRegistry.refFor(entityClass, entityId))

  def eventStream[Event <: AggregateEvent[Event]](aggregateTag: AggregateEventTag[Event], fromOffset: Optional[UUID]): javadsl.Source[Pair[Event, UUID], NotUsed] =
    persistentEntityRegistry.eventStream(aggregateTag, fromOffset).asJava

  def gracefulShutdown(timeout: FiniteDuration): CompletionStage[Done] =
    persistentEntityRegistry.gracefulShutdown(timeout).toJava
}
