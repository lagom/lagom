/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import com.lightbend.lagom.persistence.CorePersistentEntityRegistry
import akka.actor.ActorSystem
import com.lightbend.lagom.internal.persistence.InternalPersistentEntityRegistry

/**
 * At system startup all [[PersistentEntity]] classes must be registered here
 * with [[PersistentEntityRegistry#register]].
 *
 * Later, [[PersistentEntityRef]] can be retrieved with [[PersistentEntityRegistry#refFor]].
 * Commands are sent to a [[PersistentEntity]] using a `PersistentEntityRef`.
 */
class PersistentEntityRegistry(system: ActorSystem) extends InternalPersistentEntityRegistry(system)
