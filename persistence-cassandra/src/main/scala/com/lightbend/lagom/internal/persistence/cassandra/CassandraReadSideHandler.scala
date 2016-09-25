/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import java.util.concurrent.CompletionStage
import java.util.function.BiFunction

import com.datastax.driver.core.{ BatchStatement, BoundStatement }
import com.lightbend.lagom.javadsl.persistence.{ AggregateEvent, AggregateEventTag, Offset }
import java.util.{ UUID, List => JList }

import akka.Done
import akka.japi.Pair
import akka.stream.ActorAttributes
import akka.stream.javadsl.Flow
import com.lightbend.lagom.javadsl.persistence.Offset.TimeBasedUUID
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.javadsl.persistence.cassandra.{ CassandraReadSideProcessor, CassandraSession }
import org.pcollections.TreePVector
import org.slf4j.LoggerFactory

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future }

private[cassandra] abstract class CassandraReadSideHandler[Event <: AggregateEvent[Event], Handler](
  session: CassandraSession, handlers: Map[Class[_ <: Event], Handler], dispatcher: String
)(implicit ec: ExecutionContext) extends ReadSideHandler[Event] {

  private val log = LoggerFactory.getLogger(this.getClass)

  protected def invoke(handler: Handler, event: Event, offset: Offset): CompletionStage[JList[BoundStatement]]

  override def handle(): Flow[Pair[Event, Offset], Done, _] = {
    akka.stream.scaladsl.Flow[Pair[Event, Offset]].mapAsync(parallelism = 1) { pair =>
      handlers.get(pair.first.getClass.asInstanceOf[Class[Event]]) match {
        case Some(handler) =>
          for {
            statements <- invoke(handler, pair.first, pair.second).toScala
            done <- statements.size match {
              case 0 => Future.successful(Done.getInstance())
              case 1 => session.executeWrite(statements.get(0)).toScala
              case _ =>
                val batch = new BatchStatement
                val iter = statements.iterator()
                while (iter.hasNext)
                  batch.add(iter.next)
                session.executeWriteBatch(batch).toScala
            }
          } yield done
        case None =>
          if (log.isDebugEnabled)
            log.debug("Unhandled event [{}]", pair.first.getClass.getName)
          Future.successful(Done.getInstance())
      }
    }.withAttributes(ActorAttributes.dispatcher(dispatcher)).asJava
  }
}

private[cassandra] object CassandraAutoReadSideHandler {
  type Handler[Event] = (_ <: Event, Offset) => CompletionStage[JList[BoundStatement]]
}

import CassandraAutoReadSideHandler.Handler

private[cassandra] class CassandraAutoReadSideHandler[Event <: AggregateEvent[Event]](
  session:               CassandraSession,
  offsetStore:           CassandraOffsetStore,
  handlers:              Map[Class[_ <: Event], Handler[Event]],
  globalPrepareCallback: () => CompletionStage[Done],
  prepareCallback:       AggregateEventTag[Event] => CompletionStage[Done],
  readProcessorId:       String,
  dispatcher:            String
)(implicit ec: ExecutionContext) extends CassandraReadSideHandler[Event, Handler[Event]](
  session, handlers, dispatcher
) {

  @volatile
  private var offsetDao: CassandraOffsetDao = _

  override protected def invoke(handler: Handler[Event], event: Event, offset: Offset): CompletionStage[JList[BoundStatement]] = {
    val boundStatements = {
      for {
        statements <- (handler.asInstanceOf[(Event, Offset) => CompletionStage[JList[BoundStatement]]].apply(event, offset).toScala)
      } yield TreePVector.from(statements).plus(offsetDao.bindSaveOffset(offset)).asInstanceOf[JList[BoundStatement]]
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
      dao.loadedOffset
    }).toJava
  }
}

private[cassandra] class LegacyCassandraReadSideHandler[Event <: AggregateEvent[Event]](
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
