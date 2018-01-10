/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import com.lightbend.lagom.scaladsl.persistence.{ AbstractPersistentEntityActorSpec, TestEntitySerializerRegistry }

class CassandraPersistentEntityActorSpec extends CassandraPersistenceSpec(TestEntitySerializerRegistry) with AbstractPersistentEntityActorSpec
