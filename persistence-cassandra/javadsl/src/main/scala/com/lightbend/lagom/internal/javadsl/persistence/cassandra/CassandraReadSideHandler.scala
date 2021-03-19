/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.persistence.cassandra

import java.util
import java.util.concurrent.CompletionStage
import java.util.{ List => JList }
import akka.Done
import akka.japi.Pair
import akka.persistence.query.Offset
import akka.stream.ActorAttributes
import akka.stream.javadsl.Flow
import com.datastax.driver.core.BatchStatement
import com.datastax.driver.core.BoundStatement
import com.lightbend.lagom.internal.javadsl.persistence.OffsetAdapter
import com.lightbend.lagom.internal.persistence.cassandra.CassandraOffsetDao
import com.lightbend.lagom.internal.persistence.cassandra.CassandraOffsetStore
import com.lightbend.lagom.internal.persistence.cassandra.CassandraReadSideSettings
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession
import com.lightbend.lagom.javadsl.persistence.AggregateEvent
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag
import com.lightbend.lagom.javadsl.persistence.{ Offset => LagomOffset }
import org.pcollections.TreePVector
import org.slf4j.LoggerFactory

import java.util
import java.util
import java.util.Collections
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * Internal API
 */
private[cassandra] abstract class CassandraReadSideHandler[Event <: AggregateEvent[Event], Handler](
    session: CassandraSession,
    cassandraReadSideSettings: CassandraReadSideSettings,
    handlers: Map[Class[_ <: Event], Handler],
    dispatcher: String
)(implicit ec: ExecutionContext)
    extends ReadSideHandler[Event] {
  private val log = LoggerFactory.getLogger(this.getClass)

  protected def invoke(handler: Handler, event: Event, offset: LagomOffset): CompletionStage[JList[BoundStatement]]
  protected def offsetStatement(offset: Offset): BoundStatement

  override def handle(): Flow[Pair[Event, LagomOffset], Done, _] = {
    def executeStatements(statements: JList[BoundStatement]): Future[Done] = {
      if (statements.isEmpty) {
        Future.successful(Done.getInstance())
      } else {
        val batch = new BatchStatement
        batch.addAll(statements)
        batch.setConsistencyLevel(cassandraReadSideSettings.writeConsistency)
        session.executeWriteBatch(batch).toScala
      }
    }

    akka.stream.scaladsl
      .Flow[Pair[Event, LagomOffset]]
      .mapAsync(parallelism = 1) { pair =>
        val Pair(event, offset) = pair
        val eventClass          = event.getClass

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

        for {
          statements <- invoke(handler, event, offset).toScala
          _          <- executeStatements(statements)
          // important: only commit offset once read view
          // statements has completed successfully
          done <- executeStatements(util.Arrays.asList(offsetStatement(OffsetAdapter.dslOffsetToOffset(offset))))
        } yield done
      }
      .withAttributes(ActorAttributes.dispatcher(dispatcher))
      .asJava
  }
}

/**
 * Internal API
 */
private[cassandra] object CassandraAutoReadSideHandler {
  type Handler[Event] = (_ <: Event, LagomOffset) => CompletionStage[JList[BoundStatement]]

  def emptyHandler[Event, E <: Event]: Handler[Event] =
    (_: E, _: LagomOffset) => Future.successful(util.Collections.emptyList[BoundStatement]()).toJava
}

/**
 * Internal API
 */
private[cassandra] final class CassandraAutoReadSideHandler[Event <: AggregateEvent[Event]](
    session: CassandraSession,
    cassandraReadSideSettings: CassandraReadSideSettings,
    offsetStore: CassandraOffsetStore,
    handlers: Map[Class[_ <: Event], CassandraAutoReadSideHandler.Handler[Event]],
    globalPrepareCallback: () => CompletionStage[Done],
    prepareCallback: AggregateEventTag[Event] => CompletionStage[Done],
    readProcessorId: String,
    dispatcher: String
)(implicit ec: ExecutionContext)
    extends CassandraReadSideHandler[Event, CassandraAutoReadSideHandler.Handler[Event]](
      session,
      cassandraReadSideSettings,
      handlers,
      dispatcher
    ) {
  import CassandraAutoReadSideHandler.Handler

  @volatile
  private var offsetDao: CassandraOffsetDao = _

  protected override def invoke(
      handler: Handler[Event],
      event: Event,
      offset: LagomOffset
  ): CompletionStage[JList[BoundStatement]] = {
    handler
      .asInstanceOf[(Event, LagomOffset) => CompletionStage[JList[BoundStatement]]]
      .apply(event, offset)
  }

  override def offsetStatement(offset: Offset): BoundStatement =
    offsetDao.bindSaveOffset(offset)

  override def globalPrepare(): CompletionStage[Done] = {
    globalPrepareCallback.apply()
  }

  override def prepare(tag: AggregateEventTag[Event]): CompletionStage[LagomOffset] = {
    (for {
      _   <- prepareCallback.apply(tag).toScala
      dao <- offsetStore.prepare(readProcessorId, tag.tag)
    } yield {
      offsetDao = dao
      OffsetAdapter.offsetToDslOffset(dao.loadedOffset)
    }).toJava
  }
}
