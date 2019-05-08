/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.jdbc

import java.lang.Long
import java.util.concurrent.CompletionStage

import com.lightbend.lagom.internal.javadsl.persistence.jdbc.JdbcPersistentEntityRegistry
import com.lightbend.lagom.internal.javadsl.persistence.jdbc.JdbcSessionImpl
import com.lightbend.lagom.javadsl.persistence.TestEntity.Evt
import com.lightbend.lagom.javadsl.persistence._
import play.api.inject.guice.GuiceInjectorBuilder

import scala.concurrent.duration._

class JdbcReadSideSpec extends JdbcPersistenceSpec with AbstractReadSideSpec {
  private lazy val injector                            = new GuiceInjectorBuilder().build()
  protected override lazy val persistentEntityRegistry = new JdbcPersistentEntityRegistry(system, injector, slick)

  override def processorFactory(): ReadSideProcessor[Evt] =
    new JdbcTestEntityReadSide.TestEntityReadSideProcessor(jdbcReadSide)

  protected lazy val session: JdbcSession = new JdbcSessionImpl(slick)
  private lazy val readSide               = new JdbcTestEntityReadSide(session)

  override def getAppendCount(id: String): CompletionStage[Long] = readSide.getAppendCount(id)

  override def afterAll(): Unit = {
    persistentEntityRegistry.gracefulShutdown(5.seconds)
    super.afterAll()
  }

}
