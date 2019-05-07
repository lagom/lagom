/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.testkit

import akka.Done
import akka.persistence.query.Offset
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement
import com.lightbend.lagom.scaladsl.persistence.ReadSide
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReadSideTestDriver(implicit val materializer: Materializer, ec: ExecutionContext) extends ReadSide {
  private var processors = Map.empty[Class[_], Seq[Future[(ReadSideHandler[_], Offset)]]]

  override def register[Event <: AggregateEvent[Event]](processorFactory: => ReadSideProcessor[Event]): Unit = {
    val processor = processorFactory
    val eventTags = processor.aggregateTags
    val handler   = processor.buildHandler()
    val future = for {
      _      <- handler.globalPrepare()
      offset <- handler.prepare(eventTags.head)
    } yield {
      handler -> offset
    }
    synchronized {
      val handlers = processors.getOrElse(eventTags.head.eventType, Nil)
      processors += (eventTags.head.eventType -> (handlers :+ future))
    }
  }

  def feed[Event <: AggregateEvent[Event]](entityId: String, event: Event, offset: Offset): Future[Done] = {
    processors.get(event.aggregateTag.eventType) match {
      case None => sys.error(s"No processor registered for Event ${event.aggregateTag.eventType.getCanonicalName}")
      case Some(handlerFutures) =>
        for {
          handlers <- Future.sequence(handlerFutures)
          _ <- Future.sequence(handlers.map {
            case (handler: ReadSideHandler[Event], _) =>
              Source
                .single(new EventStreamElement(entityId, event, offset))
                .via(handler.handle())
                .runWith(Sink.ignore)
          })
        } yield {
          Done
        }
    }
  }
}
