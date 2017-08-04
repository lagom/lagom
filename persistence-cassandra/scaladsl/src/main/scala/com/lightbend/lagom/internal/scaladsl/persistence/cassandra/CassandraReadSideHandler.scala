/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence.cassandra

import java.util

import akka.persistence.query.Offset
import akka.stream.ActorAttributes
import akka.stream.scaladsl.Flow
import akka.{ Done, NotUsed }
import com.datastax.driver.core.{ BatchStatement, BoundStatement }
import com.lightbend.lagom.internal.persistence.cassandra.{ CassandraOffsetDao, CassandraOffsetStore }
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence._
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession
import org.slf4j.LoggerFactory

import scala.collection.immutable
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

  protected def invoke(handler: Handler, event: EventStreamElement[Event]): Future[immutable.Seq[BoundStatement]]

  override def handle(): Flow[EventStreamElement[Event], Done, NotUsed] = {

    def executeStatements(statements: Seq[BoundStatement]): Future[Done] =
      statements.size match {
        case 0 => Future.successful(Done)
        case 1 => session.executeWrite(statements.head)
        case _ =>
          val batch = new BatchStatement
          val iter = statements.iterator
          while (iter.hasNext)
            batch.add(iter.next)
          session.executeWriteBatch(batch)
      }

    Flow[EventStreamElement[Event]]
      .mapAsync(parallelism = 1) { elem =>

        val handler =
          handlers.getOrElse(
            // lookup handler
            elem.event.getClass.asInstanceOf[Class[Event]],
            // fallback to empty handle if none
            CassandraAutoReadSideHandler.emptyHandler.asInstanceOf[Handler]
          )

        invoke(handler, elem).flatMap(executeStatements)

      }.withAttributes(ActorAttributes.dispatcher(dispatcher))
  }
}

/**
 * Internal API
 */
private[cassandra] object CassandraAutoReadSideHandler {

  type Handler[Event] = (EventStreamElement[_ <: Event]) => Future[immutable.Seq[BoundStatement]]

  def emptyHandler[Event]: Handler[Event] =
    (_) => Future.successful(immutable.Seq.empty[BoundStatement])
}

/**
 * Internal API
 */
private[cassandra] final class CassandraAutoReadSideHandler[Event <: AggregateEvent[Event]](
  session:               CassandraSession,
  offsetStore:           CassandraOffsetStore,
  handlers:              Map[Class[_ <: Event], CassandraAutoReadSideHandler.Handler[Event]],
  globalPrepareCallback: () => Future[Done],
  prepareCallback:       AggregateEventTag[Event] => Future[Done],
  readProcessorId:       String,
  dispatcher:            String
)(implicit ec: ExecutionContext)
  extends CassandraReadSideHandler[Event, CassandraAutoReadSideHandler.Handler[Event]](
    session, handlers, dispatcher
  ) {

  import CassandraAutoReadSideHandler.Handler

  @volatile
  private var offsetDao: CassandraOffsetDao = _

  override protected def invoke(handler: Handler[Event], element: EventStreamElement[Event]): Future[immutable.Seq[BoundStatement]] = {
    for {
      statements <- handler
        .asInstanceOf[EventStreamElement[Event] => Future[immutable.Seq[BoundStatement]]]
        .apply(element)
    } yield statements :+ offsetDao.bindSaveOffset(element.offset)
  }

  protected def offsetStatement(offset: Offset): immutable.Seq[BoundStatement] =
    immutable.Seq(offsetDao.bindSaveOffset(offset))

  override def globalPrepare(): Future[Done] = {
    globalPrepareCallback.apply()
  }

  override def prepare(tag: AggregateEventTag[Event]): Future[Offset] = {
    for {
      _ <- prepareCallback.apply(tag)
      dao <- offsetStore.prepare(readProcessorId, tag.tag)
    } yield {
      offsetDao = dao
      dao.loadedOffset
    }
  }
}
