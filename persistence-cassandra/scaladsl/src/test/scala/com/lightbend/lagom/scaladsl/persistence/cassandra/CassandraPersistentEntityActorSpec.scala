/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.cassandra

import com.lightbend.lagom.scaladsl.persistence.AbstractPersistentEntityActorSpec
import com.lightbend.lagom.scaladsl.persistence.TestEntitySerializerRegistry

class CassandraPersistentEntityActorSpec
    extends CassandraPersistenceSpec(TestEntitySerializerRegistry)
    with AbstractPersistentEntityActorSpec
