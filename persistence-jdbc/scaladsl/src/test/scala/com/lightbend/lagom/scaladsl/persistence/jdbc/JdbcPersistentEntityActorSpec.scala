/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.jdbc

import com.lightbend.lagom.scaladsl.persistence.{ AbstractPersistentEntityActorSpec, TestEntitySerializerRegistry }

class JdbcPersistentEntityActorSpec extends JdbcPersistenceSpec(TestEntitySerializerRegistry) with AbstractPersistentEntityActorSpec
