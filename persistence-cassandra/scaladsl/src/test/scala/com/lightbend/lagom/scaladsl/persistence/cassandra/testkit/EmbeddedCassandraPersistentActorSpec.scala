/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra.testkit

import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceSpec
import com.lightbend.lagom.scaladsl.persistence.testkit.AbstractEmbeddedPersistentActorSpec
import com.lightbend.lagom.scaladsl.playjson.EmptyJsonSerializerRegistry

class EmbeddedCassandraPersistentActorSpec extends CassandraPersistenceSpec(EmptyJsonSerializerRegistry) with AbstractEmbeddedPersistentActorSpec
