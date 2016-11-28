/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jdbc

import java.sql.Connection
import java.util.concurrent.CompletionStage
import javax.inject.{ Inject, Singleton }

import akka.Done
import akka.japi.Pair
import akka.stream.javadsl.Flow
import com.lightbend.lagom.internal.javadsl.persistence.OffsetAdapter
import com.lightbend.lagom.internal.persistence.jdbc.SlickOffsetDao
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.javadsl.persistence.jdbc.JdbcReadSide
import com.lightbend.lagom.javadsl.persistence.jdbc.JdbcReadSide._
import com.lightbend.lagom.javadsl.persistence.{ AggregateEvent, AggregateEventTag, Offset }
import org.slf4j.LoggerFactory

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future }

/**
 * INTERNAL API
 */
@Singleton
private[lagom] class JdbcReadSideImpl @Inject() (slick: SlickProvider, offsetStore: JavadslJdbcOffsetStore)(implicit val ec: ExecutionContext) extends JdbcReadSide {

  private val log = LoggerFactory.getLogger(this.getClass)

  override def builder[Event <: AggregateEvent[Event]](readSideId: String): ReadSideHandlerBuilder[Event] = new ReadSideHandlerBuilder[Event] {
    var globalPrepare: Connection => Unit = { _ => () }
    var prepare: (Connection, AggregateEventTag[Event]) => Unit = (_, _) => ()
    var eventHandlers = Map.empty[Class[_ <: Event], (Connection, _ <: Event, Offset) => Unit]

    override def setGlobalPrepare(callback: ConnectionConsumer): ReadSideHandlerBuilder[Event] = {
      globalPrepare = callback.accept
      this
    }

    override def setPrepare(callback: ConnectionBiConsumer[AggregateEventTag[Event]]): ReadSideHandlerBuilder[Event] = {
      prepare = callback.accept
      this
    }

    override def setEventHandler[E <: Event](eventClass: Class[E], handler: ConnectionBiConsumer[E]): ReadSideHandlerBuilder[Event] = {
      eventHandlers += (eventClass -> ((c: Connection, e: E, o: Offset) => handler.accept(c, e)))
      this
    }

    override def setEventHandler[E <: Event](eventClass: Class[E], handler: ConnectionTriConsumer[E, Offset]): ReadSideHandlerBuilder[Event] = {
      eventHandlers += (eventClass -> handler.accept _)
      this
    }

    override def build(): ReadSideHandler[Event] = new JdbcReadSideHandler[Event](readSideId, globalPrepare, prepare, eventHandlers)
  }

  private class JdbcReadSideHandler[Event <: AggregateEvent[Event]](
    readSideId:            String,
    globalPrepareCallback: Connection => Unit,
    prepareCallback:       (Connection, AggregateEventTag[Event]) => Unit,
    eventHandlers:         Map[Class[_ <: Event], (Connection, _ <: Event, Offset) => Unit]
  ) extends ReadSideHandler[Event] {

    import slick.profile.api._

    @volatile
    private var offsetDao: SlickOffsetDao = _

    override def globalPrepare(): CompletionStage[Done] = {
      slick.ensureTablesCreated().flatMap { _ =>
        slick.db.run {
          SimpleDBIO { ctx =>
            globalPrepareCallback(ctx.connection)
            Done.getInstance()
          }
        }
      }.toJava
    }

    override def prepare(tag: AggregateEventTag[Event]): CompletionStage[Offset] = {
      (for {
        _ <- slick.db.run {
          SimpleDBIO { ctx =>
            prepareCallback(ctx.connection, tag)
          }
        }
        dao <- offsetStore.prepare(readSideId, tag.tag)
      } yield {
        offsetDao = dao
        OffsetAdapter.offsetToDslOffset(dao.loadedOffset)
      }).toJava
    }

    override def handle(): Flow[Pair[Event, Offset], Done, Any] = {
      akka.stream.scaladsl.Flow[Pair[Event, Offset]].mapAsync(parallelism = 1) { pair =>
        eventHandlers.get(pair.first.getClass) match {
          case Some(handler) =>
            slick.db.run {
              (for {
                _ <- SimpleDBIO { ctx =>
                  handler.asInstanceOf[(Connection, Event, Offset) => Unit](ctx.connection, pair.first, pair.second)
                }
                _ <- offsetDao.updateOffsetQuery(OffsetAdapter.dslOffsetToOffset(pair.second))
              } yield {
                Done.getInstance()
              }).transactionally
            }
          case None =>
            if (log.isDebugEnabled)
              log.debug("Unhandled event [{}]", pair.first.getClass.getName)
            Future.successful(Done.getInstance())
        }
      }.asJava
    }
  }
}
