/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import org.slf4j.LoggerFactory
import com.lightbend.lagom.javadsl.api.ServiceInfo
import akka.stream.Materializer
import javax.inject.Inject

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.broker.TaggedOffsetTopicProducer
import com.lightbend.lagom.internal.javadsl.api.MethodTopicHolder
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory
import com.lightbend.lagom.internal.javadsl.persistence.OffsetStore
import com.lightbend.lagom.internal.javadsl.server.ResolvedServices
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall

class RegisterTopicProducers @Inject() (resolvedServices: ResolvedServices, topicFactory: TopicFactory,
                                        info: ServiceInfo, actorSystem: ActorSystem, offsetStore: OffsetStore)(implicit ec: ExecutionContext, mat: Materializer) {

  private val log = LoggerFactory.getLogger(classOf[RegisterTopicProducers])
  private val kafkaConfig = KafkaConfig(actorSystem.settings.config)

  // Goes through the services' descriptors and publishes the streams registered in
  // each of the service's topic method implementation.
  for {
    service <- resolvedServices.services
    tc <- service.descriptor.topicCalls().asScala
    topicCall = tc.asInstanceOf[TopicCall[AnyRef]]
  } {
    topicCall.topicHolder match {
      case holder: MethodTopicHolder =>
        val topicProducer = holder.create(service.service)
        val topicId = topicCall.topicId

        topicFactory.create(topicCall) match {
          case topicImpl: KafkaTopic[AnyRef] =>

            topicProducer match {
              case tagged: TaggedOffsetTopicProducer[AnyRef, _] =>
                Producer[AnyRef](kafkaConfig, topicCall, actorSystem).publishTaggedOffsetProducer(tagged, offsetStore)
              case other => log.warn {
                s"Unknown topic producer ${other.getClass.getName}. " +
                  s"This will likely result in no events published to topic ${topicId.value} by service ${info.serviceName}."
              }
            }

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
