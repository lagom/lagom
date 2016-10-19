/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.jdbc.testkit

import com.lightbend.lagom.scaladsl.persistence.jdbc.JdbcPersistenceSpec
import com.lightbend.lagom.scaladsl.persistence.testkit.AbstractEmbeddedPersistentActorSpec

class EmbeddedJdbcPersistentActorSpec extends JdbcPersistenceSpec with AbstractEmbeddedPersistentActorSpec
