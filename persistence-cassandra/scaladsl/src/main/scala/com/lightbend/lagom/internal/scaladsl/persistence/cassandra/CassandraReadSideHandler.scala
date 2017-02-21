/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence.cassandra

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

    def invokeHandler(handler: Handler, elem: EventStreamElement[Event]): Future[Done] = {
      for {
        statements <- invoke(handler, elem)
        done <- statements.size match {
          case 0 => Future.successful(Done)
          case 1 => session.executeWrite(statements.head)
          case _ =>
            val batch = new BatchStatement
            val iter = statements.iterator
            while (iter.hasNext)
              batch.add(iter.next)
            session.executeWriteBatch(batch)
        }
      } yield done
    }

    Flow[EventStreamElement[Event]].mapAsync(parallelism = 1) { elem =>
      handlers.get(elem.event.getClass.asInstanceOf[Class[Event]]) match {
        case Some(handler) => invokeHandler(handler, elem)
        case None =>
          if (log.isDebugEnabled)
            log.debug("Unhandled event [{}]", elem.event.getClass.getName)
          Future.successful(Done)
      }
    }.withAttributes(ActorAttributes.dispatcher(dispatcher))
  }
}

/**
 * Internal API
 */
private[cassandra] object CassandraAutoReadSideHandler {
  type Handler[Event] = (EventStreamElement[_ <: Event]) => Future[immutable.Seq[BoundStatement]]
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
