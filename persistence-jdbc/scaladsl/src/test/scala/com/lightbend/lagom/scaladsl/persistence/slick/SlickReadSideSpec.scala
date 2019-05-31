/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.slick

import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.JdbcPersistentEntityRegistry
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence._

import scala.concurrent.Future
import scala.concurrent.duration.DurationDouble

class SlickReadSideSpec extends SlickPersistenceSpec(TestEntitySerializerRegistry) with AbstractReadSideSpec {

  import system.dispatcher

  protected override lazy val persistentEntityRegistry = new JdbcPersistentEntityRegistry(system, slick)

  override def processorFactory(): ReadSideProcessor[Evt] =
    new SlickTestEntityReadSide.TestEntityReadSideProcessor(slickReadSide, slick.db, slick.profile)

  lazy val readSide = new SlickTestEntityReadSide(slick.db, slick.profile)

  override def getAppendCount(id: String): Future[Long] = readSide.getAppendCount(id)

  override def afterAll(): Unit = {
    super.afterAll()
  }
}
