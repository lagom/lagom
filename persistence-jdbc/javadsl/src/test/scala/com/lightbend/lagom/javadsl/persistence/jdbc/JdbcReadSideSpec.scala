/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jdbc

import java.lang.Long
import java.util.concurrent.CompletionStage

import com.google.inject.Guice
import com.lightbend.lagom.internal.javadsl.persistence.jdbc.JdbcPersistentEntityRegistry
import com.lightbend.lagom.javadsl.persistence.TestEntity.Evt
import com.lightbend.lagom.javadsl.persistence._

import scala.concurrent.duration._

class JdbcReadSideSpec extends JdbcPersistenceSpec with AbstractReadSideSpec {
  private lazy val injector = Guice.createInjector()
  override protected lazy val persistentEntityRegistry = new JdbcPersistentEntityRegistry(system, injector, slick)

  override def processorFactory(): ReadSideProcessor[Evt] =
    new JdbcTestEntityReadSide.TestEntityReadSideProcessor(jdbcReadSide)

  private lazy val readSide = new JdbcTestEntityReadSide(session)

  override def getAppendCount(id: String): CompletionStage[Long] = readSide.getAppendCount(id)

  override def afterAll(): Unit = {
    persistentEntityRegistry.gracefulShutdown(5.seconds)
    super.afterAll()
  }

}
