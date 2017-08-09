/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.jdbc

import akka.persistence.query.Sequence
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.JdbcPersistentEntityRegistry
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence._

import scala.concurrent.Future
import scala.concurrent.duration._

class JdbcReadSideSpec extends JdbcPersistenceSpec(TestEntitySerializerRegistry) with AbstractReadSideSpec {
  override protected lazy val persistentEntityRegistry = new JdbcPersistentEntityRegistry(system, slick)

  override def processorFactory(): ReadSideProcessor[Evt] =
    new JdbcTestEntityReadSide.TestEntityReadSideProcessor(jdbcReadSide)

  lazy val readSide = new JdbcTestEntityReadSide(session)

  override def getAppendCount(id: String): Future[Long] = readSide.getAppendCount(id)

  override def afterAll(): Unit = {
    persistentEntityRegistry.gracefulShutdown(5.seconds)
    super.afterAll()
  }

}
