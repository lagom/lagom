/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra.testkit

import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraPersistenceSpec
import com.lightbend.lagom.javadsl.persistence.testkit.AbstractEmbeddedPersistentActorSpec

class EmbeddedCassandraPersistentActorSpec extends CassandraPersistenceSpec with AbstractEmbeddedPersistentActorSpec
