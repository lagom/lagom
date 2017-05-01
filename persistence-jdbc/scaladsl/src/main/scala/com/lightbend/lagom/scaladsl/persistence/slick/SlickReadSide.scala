/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.slick

import slick.dbio.{ DBIOAction, NoStream }
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend.Database
import com.lightbend.lagom.scaladsl.persistence._

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

trait SlickReadSide {

  val db: Database
  val profile: JdbcProfile
  implicit val executionContext: ExecutionContext

  def builder[Event <: AggregateEvent[Event]](readSideId: String): ReadSideHandlerBuilder[Event]

  trait ReadSideHandlerBuilder[Event <: AggregateEvent[Event]] {

    def setGlobalPrepare(dbio: DBIOAction[Any, _, _]): ReadSideHandlerBuilder[Event]

    def setPrepare(callback: (AggregateEventTag[Event]) => DBIOAction[Any, NoStream, Nothing]): ReadSideHandlerBuilder[Event]

    def setEventHandler[E <: Event: ClassTag](handler: (EventStreamElement[E]) => DBIOAction[Any, NoStream, Nothing]): ReadSideHandlerBuilder[Event]

    def build(): ReadSideProcessor.ReadSideHandler[Event]
  }
}
