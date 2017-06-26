/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra.testkit

import com.lightbend.lagom.internal.javadsl.persistence.testkit.CassandraTestConfig
import com.lightbend.lagom.javadsl.persistence.testkit.AbstractTestUtil
import com.typesafe.config.Config

object TestUtil extends AbstractTestUtil {

  def persistenceConfig(testName: String, cassandraPort: Int): Config = CassandraTestConfig.persistenceConfig(testName, cassandraPort)

}
