/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jpa

import java.lang.Long
import java.util.concurrent.CompletionStage

import com.google.inject.Guice
import com.lightbend.lagom.internal.javadsl.persistence.jdbc.JdbcPersistentEntityRegistry
import com.lightbend.lagom.javadsl.persistence.TestEntity.Evt
import com.lightbend.lagom.javadsl.persistence._
import com.lightbend.lagom.javadsl.persistence.jpa.{ JpaReadSide, TestEntityJpaReadSide }

import scala.concurrent.duration._

class JpaReadSideImplSpec extends JpaPersistenceSpec with AbstractReadSideSpec {
  private lazy val injector = Guice.createInjector()
  override protected lazy val persistentEntityRegistry = new JdbcPersistentEntityRegistry(system, injector, slick)

  private lazy val jpaReadSide: JpaReadSide = new JpaReadSideImpl(jpa, offsetStore)

  def processorFactory(): ReadSideProcessor[Evt] =
    new TestEntityJpaReadSide.TestEntityJpaReadSideProcessor(jpaReadSide)

  private lazy val readSide = new TestEntityJpaReadSide(jpa)

  def getAppendCount(id: String): CompletionStage[Long] = readSide.getAppendCount(id)

  override def afterAll(): Unit = {
    persistentEntityRegistry.gracefulShutdown(5.seconds)
    super.afterAll()
  }

}
