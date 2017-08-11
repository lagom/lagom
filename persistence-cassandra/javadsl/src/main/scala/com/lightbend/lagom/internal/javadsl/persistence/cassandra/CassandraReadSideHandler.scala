/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.cassandra

import java.util
import java.util.concurrent.CompletionStage
import java.util.function.BiFunction
import java.util.{ UUID, List => JList }

import akka.Done
import akka.japi.Pair
import akka.stream.ActorAttributes
import akka.stream.javadsl.Flow
import com.datastax.driver.core.{ BatchStatement, BoundStatement }
import com.lightbend.lagom.internal.javadsl.persistence.OffsetAdapter
import com.lightbend.lagom.internal.persistence.cassandra.{ CassandraOffsetDao, CassandraOffsetStore }
import com.lightbend.lagom.javadsl.persistence.Offset.TimeBasedUUID
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.javadsl.persistence.cassandra.{ CassandraReadSideProcessor, CassandraSession }
import com.lightbend.lagom.javadsl.persistence.{ AggregateEvent, AggregateEventTag, Offset }
import org.pcollections.TreePVector
import org.slf4j.LoggerFactory

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future }

/**
 * Internal API
 */
private[cassandra] abstract class CassandraReadSideHandler[Event <: AggregateEvent[Event], Handler](
  session:    CassandraSession,
  handlers:   Map[Class[_ <: Event], Handler],
  dispatcher: String
)(implicit ec: ExecutionContext) extends ReadSideHandler[Event] {

  private val log = LoggerFactory.getLogger(this.getClass)

  protected def invoke(handler: Handler, event: Event, offset: Offset): CompletionStage[JList[BoundStatement]]

  override def handle(): Flow[Pair[Event, Offset], Done, _] = {

    def executeStatements(statements: JList[BoundStatement]): Future[Done] = {
      if (statements.isEmpty) {
        Future.successful(Done.getInstance())
      } else {
        val batch = new BatchStatement
        batch.addAll(statements)
        session.executeWriteBatch(batch).toScala
      }
    }

    akka.stream.scaladsl.Flow[Pair[Event, Offset]]
      .mapAsync(parallelism = 1) { pair =>

        val Pair(event, offset) = pair
        val eventClass = event.getClass

        val handler =
          handlers.getOrElse(
            // lookup handler
            eventClass,
            // fallback to empty handler if none
            {
              if (log.isDebugEnabled()) log.debug("Unhandled event [{}]", eventClass.getName)
              CassandraAutoReadSideHandler.emptyHandler[Event, Event].asInstanceOf[Handler]
            }
          )

        invoke(handler, event, offset).toScala.flatMap(executeStatements)

      }.withAttributes(ActorAttributes.dispatcher(dispatcher)).asJava
  }
}

/**
 * Internal API
 */
private[cassandra] object CassandraAutoReadSideHandler {

  type Handler[Event] = (_ <: Event, Offset) => CompletionStage[JList[BoundStatement]]

  def emptyHandler[Event, E <: Event]: Handler[Event] =
    (_: E, _: Offset) => Future.successful(util.Collections.emptyList[BoundStatement]()).toJava
}

/**
 * Internal API
 */
private[cassandra] final class CassandraAutoReadSideHandler[Event <: AggregateEvent[Event]](
  session:               CassandraSession,
  offsetStore:           CassandraOffsetStore,
  handlers:              Map[Class[_ <: Event], CassandraAutoReadSideHandler.Handler[Event]],
  globalPrepareCallback: () => CompletionStage[Done],
  prepareCallback:       AggregateEventTag[Event] => CompletionStage[Done],
  readProcessorId:       String,
  dispatcher:            String
)(implicit ec: ExecutionContext)
  extends CassandraReadSideHandler[Event, CassandraAutoReadSideHandler.Handler[Event]](
    session, handlers, dispatcher
  ) {

  import CassandraAutoReadSideHandler.Handler

  @volatile
  private var offsetDao: CassandraOffsetDao = _

  override protected def invoke(handler: Handler[Event], event: Event, offset: Offset): CompletionStage[JList[BoundStatement]] = {
    val boundStatements = {
      for {
        statements <- handler.asInstanceOf[(Event, Offset) => CompletionStage[JList[BoundStatement]]].apply(event, offset).toScala
      } yield {
        val akkaOffset = OffsetAdapter.dslOffsetToOffset(offset)
        TreePVector
          .from(statements)
          .plus(offsetDao.bindSaveOffset(akkaOffset))
          .asInstanceOf[JList[BoundStatement]]
      }
    }

    boundStatements.toJava
  }

  override def globalPrepare(): CompletionStage[Done] = {
    globalPrepareCallback.apply()
  }

  override def prepare(tag: AggregateEventTag[Event]): CompletionStage[Offset] = {
    (for {
      _ <- prepareCallback.apply(tag).toScala
      dao <- offsetStore.prepare(readProcessorId, tag.tag)
    } yield {
      offsetDao = dao
      OffsetAdapter.offsetToDslOffset(dao.loadedOffset)
    }).toJava
  }

}

/**
 * Internal API
 */
private[cassandra] final class LegacyCassandraReadSideHandler[Event <: AggregateEvent[Event]](
  session:            CassandraSession,
  cassandraProcessor: CassandraReadSideProcessor[Event],
  dispatcher:         String
)(implicit ec: ExecutionContext) extends CassandraReadSideHandler[Event, BiFunction[_ <: Event, UUID, CompletionStage[JList[BoundStatement]]]](
  session,
  cassandraProcessor.defineEventHandlers(new cassandraProcessor.EventHandlersBuilder).handlers,
  dispatcher
) {

  override protected def invoke(handler: BiFunction[_ <: Event, UUID, CompletionStage[JList[BoundStatement]]], event: Event, offset: Offset): CompletionStage[JList[BoundStatement]] = {
    offset match {
      case uuid: TimeBasedUUID => handler.asInstanceOf[BiFunction[Event, UUID, CompletionStage[JList[BoundStatement]]]].apply(event, uuid.value)
      case other               => throw new IllegalArgumentException("CassandraReadSideProcessor does not support offsets of type " + other.getClass.getName)
    }
  }

  override def prepare(tag: AggregateEventTag[Event]): CompletionStage[Offset] = {
    Future.successful(cassandraProcessor).flatMap(_.prepare(session).toScala).map { maybeUuid =>
      if (maybeUuid.isPresent) {
        Offset.timeBasedUUID(maybeUuid.get)
      } else Offset.NONE
    }.toJava
  }
}
