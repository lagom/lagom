/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jpa

import com.lightbend.lagom.javadsl.persistence.jdbc.JdbcPersistenceSpec
import com.lightbend.lagom.javadsl.persistence.jpa.JpaSession
import play.Configuration
import play.api.inject.DefaultApplicationLifecycle
import play.inject.DelegateApplicationLifecycle

abstract class JpaPersistenceSpec extends JdbcPersistenceSpec {
  protected lazy val config = new Configuration(system.settings.config)
  protected lazy val applicationLifecycle = new DefaultApplicationLifecycle
  protected lazy val delegateApplicationLifecycle = new DelegateApplicationLifecycle(applicationLifecycle)
  protected lazy val jpa: JpaSession = new JpaSessionImpl(config, slick, system, delegateApplicationLifecycle)

  override def afterAll(): Unit = {
    applicationLifecycle.stop()
    super.afterAll()
  }
}
