/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.persistence.jpa

import java.lang.Long
import java.util.concurrent.CompletionStage

import com.lightbend.lagom.internal.javadsl.persistence.jdbc.JdbcPersistentEntityRegistry
import com.lightbend.lagom.javadsl.persistence.TestEntity.Evt
import com.lightbend.lagom.javadsl.persistence._
import com.lightbend.lagom.javadsl.persistence.jpa.JpaReadSide
import com.lightbend.lagom.javadsl.persistence.jpa.TestEntityJpaReadSide
import play.api.inject.guice.GuiceInjectorBuilder

import scala.concurrent.duration._

class JpaReadSideImplSpec extends JpaPersistenceSpec with AbstractReadSideSpec {
  private lazy val injector                            = new GuiceInjectorBuilder().build()
  protected override lazy val persistentEntityRegistry = new JdbcPersistentEntityRegistry(system, injector, slick)

  private lazy val jpaReadSide: JpaReadSide = new JpaReadSideImpl(jpa, offsetStore)

  def processorFactory(): ReadSideProcessor[Evt] =
    new TestEntityJpaReadSide.TestEntityJpaReadSideProcessor(jpaReadSide)

  private lazy val readSide = new TestEntityJpaReadSide(jpa)

  def getAppendCount(id: String): CompletionStage[Long] = readSide.getAppendCount(id)

  override def afterAll(): Unit = {
    super.afterAll()
  }
}
