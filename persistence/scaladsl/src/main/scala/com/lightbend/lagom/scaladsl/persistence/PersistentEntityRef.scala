/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import com.lightbend.lagom.persistence.CorePersistentEntityRef

/**
 * Commands are sent to a [[PersistentEntity]] using a
 * `PersistentEntityRef`. It is retrieved with [[PersistentEntityRegistry#refFor]].
 */
trait PersistentEntityRef[Command] extends CorePersistentEntityRef[Command]
