/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra.testkit

import com.lightbend.lagom.javadsl.persistence.testkit.AbstractTestUtil
import com.typesafe.config.Config

object TestUtil extends AbstractTestUtil {

  def persistenceConfig(testName: String, cassandraPort: Int): Config = {
    com.lightbend.lagom.javadsl.persistence.testkit.TestUtil.persistenceConfig(testName, cassandraPort,
      useServiceLocator = false)
  }

}
