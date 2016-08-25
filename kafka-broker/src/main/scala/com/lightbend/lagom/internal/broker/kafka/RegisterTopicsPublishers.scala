/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import scala.collection.JavaConverters._

import org.slf4j.LoggerFactory

import com.lightbend.lagom.internal.api.InternalTopicCall
import com.lightbend.lagom.internal.api.MethodTopicHolder
import com.lightbend.lagom.internal.server.ResolvedServices

import javax.inject.Inject
import com.lightbend.lagom.internal.api.TopicFeed

// Note: This class must be eagerly instantiated at start up.
class RegisterTopicsPublishers @Inject() (resolvedServices: ResolvedServices, topics: Topics) {

  private val log = LoggerFactory.getLogger(classOf[RegisterTopicsPublishers])

  // Goes through the services' descriptors and publishes the streams registered in 
  // each of the service's topic method implementation. 
  for {
    service <- resolvedServices.services
    tc <- service.descriptor.topicCalls().asScala.toSeq
    topicCall = tc.asInstanceOf[InternalTopicCall[AnyRef]]
  } {
    topicCall.topicHolder match {
      case holder: MethodTopicHolder =>
        val topic = holder.create(service.service).asInstanceOf[TopicFeed[AnyRef]]
        val topicId = topic.topicId
        val topicImpl = topics.of(topicCall)
        topicImpl.publisher().publish(topic.messages)
        log.debug(s"Plugged publisher source for topic $topicId.")

      case other =>
        log.error {
          s"Cannot plug publisher source for topic ${topicCall.topicId}. " +
            s"Reason was that it was expected a topicHolder of type ${classOf[MethodTopicHolder]}, " +
            s"but ${other.getClass} was found instead."
        }
    }
  }
}
