/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import scala.concurrent.Future

import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId
import com.lightbend.lagom.javadsl.persistence.Offset

import akka.Done
import javax.inject.Inject
import javax.inject.Singleton

trait OffsetTracker {
  def of(topicId: TopicId, info: ServiceInfo): Future[OffsetTracker.OffsetDao]
}

object OffsetTracker {
  trait OffsetDao {
    def lastOffset: Offset
    def save(offset: Offset): Future[Done]
  }
}
