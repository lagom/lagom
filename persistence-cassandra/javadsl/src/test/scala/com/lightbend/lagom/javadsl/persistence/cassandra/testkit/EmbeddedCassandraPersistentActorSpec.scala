/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.cassandra.testkit

import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraPersistenceSpec
import com.lightbend.lagom.javadsl.persistence.testkit.AbstractEmbeddedPersistentActorSpec
import com.typesafe.config.ConfigFactory

class EmbeddedCassandraPersistentActorSpec
    extends CassandraPersistenceSpec(ConfigFactory.parseString("akka.loglevel = INFO"))
    with AbstractEmbeddedPersistentActorSpec
