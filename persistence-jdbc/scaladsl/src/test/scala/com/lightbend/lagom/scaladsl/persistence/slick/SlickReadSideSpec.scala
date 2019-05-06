/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.slick

import akka.cluster.Cluster
import akka.persistence.query.Sequence
import com.lightbend.lagom.internal.persistence.jdbc.SlickDbTestProvider
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.JdbcPersistentEntityRegistry
import com.lightbend.lagom.scaladsl.persistence.TestEntity.Evt
import com.lightbend.lagom.scaladsl.persistence._
import com.lightbend.lagom.scaladsl.persistence.jdbc.testkit.TestUtil
import play.api.inject.ApplicationLifecycle
import play.api.inject.DefaultApplicationLifecycle

import scala.concurrent.duration.DurationDouble
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SlickReadSideSpec extends SlickPersistenceSpec(TestEntitySerializerRegistry) with AbstractReadSideSpec {

  import system.dispatcher

  protected override lazy val persistentEntityRegistry = new JdbcPersistentEntityRegistry(system, slick)

  override def processorFactory(): ReadSideProcessor[Evt] =
    new SlickTestEntityReadSide.TestEntityReadSideProcessor(slickReadSide, slick.db, slick.profile)

  lazy val readSide = new SlickTestEntityReadSide(slick.db, slick.profile)

  override def getAppendCount(id: String): Future[Long] = readSide.getAppendCount(id)

  override def afterAll(): Unit = {
    persistentEntityRegistry.gracefulShutdown(5.seconds)
    super.afterAll()
  }
}
