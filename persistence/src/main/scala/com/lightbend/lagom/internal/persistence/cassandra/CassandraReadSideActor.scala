/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import java.util.{ List => JList }
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.function.BiFunction
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.Future
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.Status
import akka.event.LoggingAdapter
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.datastax.driver.core.BatchStatement
import com.datastax.driver.core.BoundStatement
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSideProcessor
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession
import com.lightbend.lagom.javadsl.persistence.AggregateEvent
import akka.stream.ActorMaterializer
import akka.Done

private[lagom] object CassandraReadSideActor {

  def props[E <: AggregateEvent[E]](tag: String, session: CassandraSession, processorFactory: () => CassandraReadSideProcessor[E]): Props =
    Props(new CassandraReadSideActor(tag, session, processorFactory()))

  private final case class Start(offset: Optional[UUID])

  private val succ = Future.successful(Done)

  private def runStream[E <: AggregateEvent[E]](
    tag:       String,
    offset:    Optional[UUID],
    queries:   CassandraReadJournal,
    processor: CassandraReadSideProcessor[E],
    session:   CassandraSession,
    log:       LoggingAdapter
  )(implicit mat: Materializer): Future[Done] = {

    val eventHandlers: Map[Class[E], BiFunction[E, UUID, CompletionStage[JList[BoundStatement]]]] =
      processor.defineEventHandlers(new processor.EventHandlersBuilder).handlers
        .asInstanceOf[Map[Class[E], BiFunction[E, UUID, CompletionStage[JList[BoundStatement]]]]]

    def process(event: E, offset: UUID): CompletionStage[JList[BoundStatement]] = {
      eventHandlers.get(event.getClass.asInstanceOf[Class[E]]) match {
        case Some(handler) => handler.apply(event.asInstanceOf[E], offset)
        case None =>
          if (log.isDebugEnabled)
            log.debug("Unhandled event [{}]", event.getClass.getName)
          processor.emptyStatements
      }
    }

    val uuidOffset = offset.orElse(queries.firstOffset)
    val boundStmts = queries.eventsByTag(tag, uuidOffset)
      .mapAsync(parallelism = 1) { env =>
        process(env.event.asInstanceOf[E], env.offset).toScala
      }
    boundStmts.mapAsync(parallelism = 1) { stmts =>
      stmts.size match {
        case 0 => succ
        case 1 => session.executeWrite(stmts.get(0)).toScala
        case _ =>
          val batch = new BatchStatement
          val iter = stmts.iterator()
          while (iter.hasNext)
            batch.add(iter.next)
          session.executeWriteBatch(batch).toScala
      }
    }.runWith(Sink.ignore)
  }

}

/**
 * The actor that runs a [[com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSideProcessor]]
 */
private[lagom] class CassandraReadSideActor[E <: AggregateEvent[E]](
  tag: String, session: CassandraSession, processor: CassandraReadSideProcessor[E]
)
  extends Actor with ActorLogging {
  import CassandraReadSideActor._
  import akka.pattern.pipe
  import context.dispatcher

  private implicit val materializer = ActorMaterializer()
  val queries = PersistenceQuery(context.system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  override def preStart(): Unit = {
    processor.prepare(session).toScala.map(Start.apply).pipeTo(self)
  }

  override def postRestart(reason: Throwable): Unit = {
    throw new IllegalStateException(s"$self must not be restarted")
  }

  def receive = {
    case Start(offset) =>
      log.debug("Starting stream for [{}] from offset [{}]", tag, offset)
      // important that the Materializer is bound to this actor so that the
      // stream is stopped when the actor is stopped. Also, this actor must not
      // be restarted.
      runStream(tag, offset, queries, processor, session, log).pipeTo(self)

    case Status.Failure(e) =>
      throw e // from pipeTo
  }

}

