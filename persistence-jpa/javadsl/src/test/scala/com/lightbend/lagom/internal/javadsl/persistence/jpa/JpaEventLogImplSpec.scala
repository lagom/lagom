/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jpa

import com.google.inject.Guice
import com.lightbend.lagom.javadsl.persistence.{ AggregateEvent, AggregateEventTag }
import com.lightbend.lagom.javadsl.persistence.jdbc.EventLogEntityType
import play.api.inject.guice.GuiceInjector

import scala.concurrent.Await
import scala.concurrent.duration._

class JpaEventLogImplSpec extends JpaPersistenceSpec {

  private lazy val injector = Guice.createInjector()
  private lazy val jpaEventLog = new JpaEventLogImpl(slick, new GuiceInjector(injector), system)

  "blah blah" should {
    "blah blah blah" in {
      import slick.profile.api._
      jpa.withTransaction { em =>
        jpaEventLog.eventLogFor(classOf[MyEntity])
          .emit(em, "foo", MyActualEvent())
      }.toCompletableFuture.get()
      System.out.println(Await.result(slick.db.run(slick.journalTables.JournalTable.result), 5.seconds))
      jpa.withTransaction { em =>
        jpaEventLog.eventLogFor(classOf[MyEntity])
          .emit(em, "foo", MyActualEvent())
      }.toCompletableFuture.get()
      System.out.println(Await.result(slick.db.run(slick.journalTables.JournalTable.result), 5.seconds))
      jpa.withTransaction { em =>
        jpaEventLog.eventLogFor(classOf[MyEntity])
          .emit(em, "foo", MyActualEvent())
      }.toCompletableFuture.get()
      System.out.println(Await.result(slick.db.run(slick.journalTables.JournalTable.result), 5.seconds))
    }
  }
}

trait MyEvent extends AggregateEvent[MyEvent] {
  override def aggregateTag = AggregateEventTag.of(classOf[MyEvent])
}
case class MyActualEvent() extends MyEvent
class MyEntity extends EventLogEntityType[MyEvent]
