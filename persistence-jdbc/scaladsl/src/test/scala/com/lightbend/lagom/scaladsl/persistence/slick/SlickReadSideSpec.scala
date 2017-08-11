/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.slick

import akka.persistence.query.Sequence
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.JdbcPersistentEntityRegistry
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence._

import scala.concurrent.duration.DurationDouble
import scala.concurrent.{ ExecutionContext, Future }

class SlickReadSideSpec(implicit ec: ExecutionContext)
  extends SlickPersistenceSpec(TestEntitySerializerRegistry)
  with AbstractReadSideSpec {

  override protected val persistentEntityRegistry = new JdbcPersistentEntityRegistry(system, slick)

  override def processorFactory(): ReadSideProcessor[Evt] =
    new SlickTestEntityReadSide.TestEntityReadSideProcessor(slickReadSide, slick.db, slick.profile)

  lazy val readSide = new SlickTestEntityReadSide(slick.db, slick.profile)

  override def getAppendCount(id: String): Future[Long] = readSide.getAppendCount(id)

  override def afterAll(): Unit = {
    persistentEntityRegistry.gracefulShutdown(5.seconds)
    super.afterAll()
  }
}
