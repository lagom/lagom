/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.slick.testkit

import com.lightbend.lagom.scaladsl.persistence.TestEntitySerializerRegistry
import com.lightbend.lagom.scaladsl.persistence.slick.SlickPersistenceSpec
import com.lightbend.lagom.scaladsl.persistence.testkit.AbstractEmbeddedPersistentActorSpec

import scala.concurrent.ExecutionContext

class EmbeddedSlickPersistentActorSpec(implicit ec: ExecutionContext)
  extends SlickPersistenceSpec(TestEntitySerializerRegistry)
  with AbstractEmbeddedPersistentActorSpec
