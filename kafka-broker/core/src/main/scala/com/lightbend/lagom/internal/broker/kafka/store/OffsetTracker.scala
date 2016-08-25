/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka.store

import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId
import com.lightbend.lagom.javadsl.api.ServiceInfo
import scala.concurrent.Future
import com.lightbend.lagom.javadsl.persistence.Offset
import akka.Done

trait OffsetTracker {
  def of(topicId: TopicId): Future[OffsetTracker.OffsetDao]
}

object OffsetTracker {
  trait OffsetDao {
    def lastOffset: Offset
    def save(offset: Offset): Future[Done]
  }
}
