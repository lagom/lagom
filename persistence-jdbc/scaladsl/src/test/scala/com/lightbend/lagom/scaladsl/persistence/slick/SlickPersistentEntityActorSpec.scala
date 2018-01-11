/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.slick

import com.lightbend.lagom.scaladsl.persistence.{ AbstractPersistentEntityActorSpec, TestEntitySerializerRegistry }

import scala.concurrent.ExecutionContext

class SlickPersistentEntityActorSpec(implicit ec: ExecutionContext)
  extends SlickPersistenceSpec(TestEntitySerializerRegistry)
  with AbstractPersistentEntityActorSpec
