/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.cassandra

import com.lightbend.lagom.javadsl.persistence.AbstractPersistentEntityActorSpec
import com.typesafe.config.ConfigFactory

class CassandraPersistentEntityActorSpec
    extends CassandraPersistenceSpec(ConfigFactory.parseString("akka.loglevel = INFO"))
    with AbstractPersistentEntityActorSpec
