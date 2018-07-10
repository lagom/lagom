/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra.testkit

import com.lightbend.lagom.internal.javadsl.persistence.testkit.CassandraTestConfig
import com.lightbend.lagom.javadsl.persistence.testkit.AbstractTestUtil
import com.typesafe.config.Config

@deprecated("Internal object, not intended for direct use.", "1.5.0")
object TestUtil extends AbstractTestUtil {

  def persistenceConfig(testName: String, cassandraPort: Int): Config = CassandraTestConfig.persistenceConfig(testName, cassandraPort)

}
