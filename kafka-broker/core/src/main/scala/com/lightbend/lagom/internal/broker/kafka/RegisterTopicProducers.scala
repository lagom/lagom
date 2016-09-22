/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

import org.slf4j.LoggerFactory

import com.lightbend.lagom.internal.api.InternalTopicCall
import com.lightbend.lagom.internal.api.MethodTopicHolder
import com.lightbend.lagom.internal.server.ResolvedServices
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.internal.api.broker.TopicFactory

import akka.stream.Materializer
import javax.inject.Inject

class RegisterTopicProducers @Inject() (resolvedServices: ResolvedServices, topicFactory: TopicFactory, info: ServiceInfo)(implicit ec: ExecutionContext, mat: Materializer) {

  private val log = LoggerFactory.getLogger(classOf[RegisterTopicProducers])

  // Goes through the services' descriptors and publishes the streams registered in 
  // each of the service's topic method implementation. 
  for {
    service <- resolvedServices.services
    tc <- service.descriptor.topicCalls().asScala.toSeq
    topicCall = tc.asInstanceOf[InternalTopicCall[AnyRef]]
  } {
    topicCall.topicHolder match {
      case holder: MethodTopicHolder =>
        val internalTopicImpl = holder.create(service.service).asInstanceOf[SingletonTopicProducer[AnyRef]]
        val topicId = internalTopicImpl.topicId

        topicFactory.create(topicCall) match {
          case topicImpl: KafkaTopic[AnyRef] =>

            val stream = internalTopicImpl.readSideStream
            topicImpl.publisher().publish(stream)
          case otherTopicImpl => log.warn {
            s"Expected Topic type ${classOf[KafkaTopic[_]].getName}, but found incompatible type ${otherTopicImpl.getClass.getName}." +
              s"This will likely result in no events published to topic ${topicId.value} by service ${info.serviceName}."
          }
        }

      case other =>
        log.error {
          s"Cannot plug publisher source for topic ${topicCall.topicId}. " +
            s"Reason was that it was expected a topicHolder of type ${classOf[MethodTopicHolder]}, " +
            s"but ${other.getClass} was found instead."
        }
    }
  }

}