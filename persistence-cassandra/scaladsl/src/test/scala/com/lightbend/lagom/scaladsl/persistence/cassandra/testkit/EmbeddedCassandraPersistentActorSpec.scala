/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra.testkit

import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceSpec
import com.lightbend.lagom.scaladsl.persistence.testkit.AbstractEmbeddedPersistentActorSpec

class EmbeddedCassandraPersistentActorSpec extends CassandraPersistenceSpec with AbstractEmbeddedPersistentActorSpec
