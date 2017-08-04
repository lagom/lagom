/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence.slick

import akka.{ Done, NotUsed }
import akka.persistence.query.Offset
import akka.stream.scaladsl.Flow
import com.lightbend.lagom.internal.persistence.jdbc.{ SlickOffsetDao, SlickOffsetStore, SlickProvider }
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.slick.SlickReadSide
import com.lightbend.lagom.scaladsl.persistence.{ AggregateEvent, AggregateEventTag, EventStreamElement }
import org.slf4j.LoggerFactory
import slick.dbio.{ DBIOAction, NoStream }
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag

/**
 * INTERNAL API
 */
private[lagom] class SlickReadSideImpl(slick: SlickProvider, offsetStore: SlickOffsetStore)(implicit val executionContext: ExecutionContext)
  extends SlickReadSide {

  private val log = LoggerFactory.getLogger(this.getClass)

  override def builder[Event <: AggregateEvent[Event]](readSideId: String): ReadSideHandlerBuilder[Event] = new ReadSideHandlerBuilder[Event] {
    var globalPrepare: DBIOAction[Any, _, _] = DBIOAction.successful(())
    var prepare: (AggregateEventTag[Event]) => DBIOAction[Any, NoStream, Nothing] = (_) => DBIOAction.successful(())
    var eventHandlers = Map.empty[Class[_ <: Event], (EventStreamElement[_ <: Event]) => DBIOAction[Any, NoStream, Nothing]]

    override def setGlobalPrepare(callback: DBIOAction[Any, _, _]): ReadSideHandlerBuilder[Event] = {
      globalPrepare = callback
      this
    }

    override def setPrepare(callback: (AggregateEventTag[Event]) => DBIOAction[Any, NoStream, Nothing]): ReadSideHandlerBuilder[Event] = {
      prepare = callback
      this
    }

    override def setEventHandler[E <: Event: ClassTag](handler: (EventStreamElement[E]) => DBIOAction[Any, NoStream, Nothing]): ReadSideHandlerBuilder[Event] = {
      val eventClass = implicitly[ClassTag[E]].runtimeClass.asInstanceOf[Class[Event]]
      eventHandlers += (eventClass -> handler.asInstanceOf[(EventStreamElement[_ <: Event]) => DBIOAction[Any, NoStream, Nothing]])
      this
    }

    override def build(): ReadSideHandler[Event] = new SlickReadSideHandler[Event](readSideId, globalPrepare, prepare, eventHandlers)
  }

  private class SlickReadSideHandler[Event <: AggregateEvent[Event]](
    readSideId:            String,
    globalPrepareCallback: DBIOAction[Any, _, _],
    prepareCallback:       (AggregateEventTag[Event]) => DBIOAction[Any, NoStream, Nothing],
    eventHandlers:         Map[Class[_ <: Event], (EventStreamElement[_ <: Event]) => DBIOAction[Any, NoStream, Nothing]]
  ) extends ReadSideHandler[Event] {

    import slick.profile.api._

    @volatile
    private var offsetDao: SlickOffsetDao = _

    override def globalPrepare(): Future[Done] =
      slick.ensureTablesCreated().flatMap { _ =>
        slick.db.run {
          globalPrepareCallback.map(_ => Done.getInstance())
        }
      }

    override def prepare(tag: AggregateEventTag[Event]): Future[Offset] =
      for {
        _ <- slick.db.run { prepareCallback(tag) }
        dao <- offsetStore.prepare(readSideId, tag.tag)
      } yield {
        offsetDao = dao
        dao.loadedOffset
      }

    override def handle(): Flow[EventStreamElement[Event], Done, NotUsed] =
      Flow[EventStreamElement[Event]]
        .mapAsync(parallelism = 1) { element =>

          val dbAction = eventHandlers.get(element.event.getClass)
            .map { handler =>
              // apply handler if found
              handler(element)
            }
            .getOrElse {
              // fallback to empty action if no handler is found
              if (log.isDebugEnabled) log.debug("Unhandled event [{}]", element.event.getClass.getName)
              DBIO.successful(())
            }
            .flatMap { _ =>
              // whatever it happens we save the offset
              offsetDao.updateOffsetQuery(element.offset)
            }
            .map(_ => Done)

          slick.db.run(dbAction.transactionally)
        }
  }
}
