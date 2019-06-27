/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.slick

import com.lightbend.lagom.scaladsl.persistence.AbstractPersistentEntityActorSpec
import com.lightbend.lagom.scaladsl.persistence.TestEntitySerializerRegistry

class SlickPersistentEntityActorSpec
    extends SlickPersistenceSpec(TestEntitySerializerRegistry)
    with AbstractPersistentEntityActorSpec
