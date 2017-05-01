/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.slick

import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.JdbcPersistentEntityRegistry
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence._

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

class SlickReadSideSpec(implicit ec: ExecutionContext)
  extends SlickPersistenceSpec(TestEntitySerializerRegistry)
  with AbstractReadSideSpec {

  override protected lazy val persistentEntityRegistry = new JdbcPersistentEntityRegistry(system, slick)

  override def processorFactory(): ReadSideProcessor[Evt] =
    new SlickTestEntityReadSide.TestEntityReadSideProcessor(slickReadSide)

  lazy val readSide = new SlickTestEntityReadSide(slickReadSide.db, slickReadSide.profile)

  override def getAppendCount(id: String): Future[Long] = readSide.getAppendCount(id)

  override def afterAll(): Unit = {
    persistentEntityRegistry.gracefulShutdown(5.seconds)
    super.afterAll()
  }
}
