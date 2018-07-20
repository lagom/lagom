/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence

/**
 * Commands to [[PersistentEntity]] are wrapped in this envelope
 * when sent via [[PersistentEntityRef]] (i.e. Cluster Sharding).
 *
 * Users should normally not use this class, but it is public case
 * for power users in case of integration with Cluster Sharding
 * entities that are not implemented with [[PersistentEntity]].
 */
final case class CommandEnvelope(entityId: String, payload: Any)
