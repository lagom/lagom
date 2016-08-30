/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import java.net.URLEncoder
import java.util
import java.util.concurrent.{ CompletableFuture, CompletionStage, TimeUnit }
import java.util.function.{ BiFunction, Function, Supplier }

import scala.concurrent.duration._
import scala.util.control.NonFatal
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.actor.SupervisorStrategy
import akka.cluster.singleton.ClusterSingletonManager
import akka.cluster.singleton.ClusterSingletonManagerSettings
import akka.pattern.BackoffSupervisor
import com.google.inject.Injector
import com.lightbend.lagom.javadsl.persistence._
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSide
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSideProcessor
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession
import javax.inject.Inject
import javax.inject.Singleton

import akka.Done
import akka.event.Logging
import com.datastax.driver.core.BoundStatement
import com.lightbend.lagom.internal.persistence.ReadSideImpl
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSide.ReadSideHandlerBuilder
import org.pcollections.{ PSequence, TreePVector }

import scala.concurrent.ExecutionContext

@Singleton
private[lagom] class CassandraReadSideImpl @Inject() (
  system: ActorSystem, session: CassandraSession, readSide: ReadSideImpl, injector: Injector
) extends CassandraReadSide {

  private val dispatcher = system.settings.config.getString("lagom.persistence.read-side.use-dispatcher")
  implicit val ec = system.dispatchers.lookup(dispatcher)

  override def register[Event <: AggregateEvent[Event]](
    processorClass: Class[_ <: CassandraReadSideProcessor[Event]]
  ): Unit = {

    readSide.registerFactory(
      () => {

        val processor = injector.getInstance(processorClass)

        new ReadSideProcessor[Event] {
          override def buildHandler(): ReadSideHandler[Event] = {
            new LegacyCassandraReadSideHandler[Event](session, processor, dispatcher)
          }

          override def aggregateTags(): PSequence[AggregateEventTag[Event]] = {
            TreePVector.singleton(processor.aggregateTag)
          }

          override def readSideName(): String = Logging.simpleName(processorClass)
        }
      }, processorClass
    )
  }

  override def builder[Event <: AggregateEvent[Event]](offsetTableName: String): ReadSideHandlerBuilder[Event] = {
    new ReadSideHandlerBuilder[Event] {
      import CassandraAutoReadSideHandler.Handler
      private var prepareCallback: () => CompletionStage[Done] =
        () => CompletableFuture.completedFuture(Done.getInstance())
      private var handlers = Map.empty[Class[_ <: Event], Handler[Event]]

      override def setPrepare(callback: Supplier[CompletionStage[Done]]): ReadSideHandlerBuilder[Event] = {
        prepareCallback = () => callback.get()
        this
      }

      override def setEventHandler[E <: Event](
        eventClass: Class[E],
        handler:    Function[E, CompletionStage[util.List[BoundStatement]]]
      ): ReadSideHandlerBuilder[Event] = {

        handlers += (eventClass -> ((event: E, offset: Offset) => handler(event)))
        this
      }

      override def setEventHandler[E <: Event](
        eventClass: Class[E],
        handler:    BiFunction[E, Offset, CompletionStage[util.List[BoundStatement]]]
      ): ReadSideHandlerBuilder[Event] = {

        handlers += (eventClass -> handler.apply _)
        this
      }

      override def build(): ReadSideHandler[Event] = {
        val offsetStore = new OffsetStore(session, offsetTableName)
        new CassandraAutoReadSideHandler[Event](session, handlers, prepareCallback, offsetStore, dispatcher)
      }
    }
  }
}
