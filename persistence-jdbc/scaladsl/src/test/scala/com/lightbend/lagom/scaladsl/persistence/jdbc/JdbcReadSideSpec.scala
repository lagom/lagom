/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.jdbc

import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.JdbcPersistentEntityRegistry
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.JdbcSessionImpl
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence._

import scala.concurrent.Future

class JdbcReadSideSpec extends JdbcPersistenceSpec(TestEntitySerializerRegistry) with AbstractReadSideSpec {
  protected override lazy val persistentEntityRegistry = new JdbcPersistentEntityRegistry(system, slick)

  override def processorFactory(): ReadSideProcessor[Evt] =
    new JdbcTestEntityReadSide.TestEntityReadSideProcessor(jdbcReadSide)

  lazy val session: JdbcSession = new JdbcSessionImpl(slick)
  lazy val readSide             = new JdbcTestEntityReadSide(session)

  override def getAppendCount(id: String): Future[Long] = readSide.getAppendCount(id)

  override def afterAll(): Unit = {
    super.afterAll()
  }
}
