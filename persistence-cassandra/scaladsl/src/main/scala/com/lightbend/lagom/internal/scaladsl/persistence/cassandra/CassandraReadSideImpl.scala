/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence.cassandra

import akka.Done
import akka.actor.ActorSystem
import com.datastax.driver.core.BoundStatement
import com.lightbend.lagom.internal.persistence.cassandra.CassandraOffsetStore
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraReadSide.ReadSideHandlerBuilder
import com.lightbend.lagom.scaladsl.persistence.cassandra.{ CassandraReadSide, CassandraSession }
import com.lightbend.lagom.scaladsl.persistence.{ AggregateEvent, AggregateEventTag, EventStreamElement }

import scala.collection.immutable
import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * Internal API
 */
private[lagom] final class CassandraReadSideImpl(
  system: ActorSystem, session: CassandraSession, offsetStore: CassandraOffsetStore
) extends CassandraReadSide {

  private val dispatcher = system.settings.config.getString("lagom.persistence.read-side.use-dispatcher")
  implicit val ec = system.dispatchers.lookup(dispatcher)

  override def builder[Event <: AggregateEvent[Event]](eventProcessorId: String): ReadSideHandlerBuilder[Event] = {
    new ReadSideHandlerBuilder[Event] {
      import CassandraAutoReadSideHandler.Handler
      private var prepareCallback: AggregateEventTag[Event] => Future[Done] = tag => Future.successful(Done)
      private var globalPrepareCallback: () => Future[Done] = () => Future.successful(Done)
      private var handlers = Map.empty[Class[_ <: Event], Handler[Event]]

      override def setGlobalPrepare(callback: () => Future[Done]): ReadSideHandlerBuilder[Event] = {
        globalPrepareCallback = callback
        this
      }

      override def setPrepare(callback: (AggregateEventTag[Event]) => Future[Done]): ReadSideHandlerBuilder[Event] = {
        prepareCallback = callback
        this
      }

      override def setEventHandler[E <: Event: ClassTag](handler: EventStreamElement[E] => Future[immutable.Seq[BoundStatement]]): ReadSideHandlerBuilder[Event] = {
        val eventClass = implicitly[ClassTag[E]].runtimeClass.asInstanceOf[Class[Event]]
        handlers += (eventClass -> handler.asInstanceOf[Handler[Event]])
        this
      }

      override def build(): ReadSideHandler[Event] = {
        new CassandraAutoReadSideHandler[Event](session, offsetStore, handlers, globalPrepareCallback, prepareCallback, eventProcessorId, dispatcher)
      }
    }
  }
}
