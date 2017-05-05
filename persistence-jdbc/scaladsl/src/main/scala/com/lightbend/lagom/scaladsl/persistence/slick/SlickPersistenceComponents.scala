/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.slick

import com.lightbend.lagom.internal.scaladsl.persistence.slick.SlickReadSideImpl
import com.lightbend.lagom.scaladsl.persistence.PersistenceComponents
import com.lightbend.lagom.scaladsl.persistence.jdbc.{ ReadSideJdbcPersistenceComponents, WriteSideJdbcPersistenceComponents }
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.JdbcProfile

/**
 * Persistence Slick components (for compile-time injection).
 */
trait SlickPersistenceComponents
  extends PersistenceComponents
  with ReadSideSlickPersistenceComponents

/**
 * Write-side persistence Slick components (for compile-time injection).
 */
trait WriteSideSlickPersistenceComponents
  extends WriteSideJdbcPersistenceComponents

/**
 * Read-side persistence Slick components (for compile-time injection).
 */
trait ReadSideSlickPersistenceComponents
  extends ReadSideJdbcPersistenceComponents {

  lazy val slickReadSide: SlickReadSide = new SlickReadSideImpl(slickProvider, slickOffsetStore)(executionContext)
  lazy val db: Database = slickProvider.db
  lazy val profile: JdbcProfile = slickProvider.profile
}
