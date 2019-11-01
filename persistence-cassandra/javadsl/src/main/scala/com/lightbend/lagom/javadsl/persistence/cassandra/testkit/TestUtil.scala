/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.cassandra.testkit

import com.lightbend.lagom.internal.persistence.testkit.PersistenceTestConfig.cassandraConfig
import com.lightbend.lagom.javadsl.persistence.testkit.AbstractTestUtil
import com.typesafe.config.Config

@deprecated("Internal class, not intended for direct use.", "1.5.0")
object TestUtil extends AbstractTestUtil {
  def persistenceConfig(testName: String, cassandraPort: Int): Config = cassandraConfig(testName, cassandraPort)
}
