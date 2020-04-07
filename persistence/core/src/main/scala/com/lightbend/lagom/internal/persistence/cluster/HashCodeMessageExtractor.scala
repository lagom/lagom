/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.cluster

object HashCodeMessageExtractor {
  def shardId(id: String, maxNumberOfShards: Int): String = {
    (math.abs(id.hashCode) % maxNumberOfShards).toString
  }
}
