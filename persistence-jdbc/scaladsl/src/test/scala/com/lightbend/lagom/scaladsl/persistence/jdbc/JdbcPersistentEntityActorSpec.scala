/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.jdbc

import com.lightbend.lagom.scaladsl.persistence.AbstractPersistentEntityActorSpec
import com.lightbend.lagom.scaladsl.persistence.TestEntitySerializerRegistry

class JdbcPersistentEntityActorSpec
    extends JdbcPersistenceSpec(TestEntitySerializerRegistry)
    with AbstractPersistentEntityActorSpec
