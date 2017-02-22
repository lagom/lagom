/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.broker.kafka

import akka.actor.ActorSystem
import akka.persistence.query.Offset
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.lightbend.internal.broker.TaggedOffsetTopicProducer
import com.lightbend.lagom.internal.broker.kafka.{ KafkaConfig, Producer }
import com.lightbend.lagom.internal.scaladsl.api.broker.TopicFactory
import com.lightbend.lagom.scaladsl.api.Descriptor.TopicCall
import com.lightbend.lagom.scaladsl.api.{ ServiceInfo, ServiceLocator }
import com.lightbend.lagom.scaladsl.api.ServiceSupport.ScalaMethodTopic
import com.lightbend.lagom.scaladsl.api.broker.kafka.KafkaProperties
import com.lightbend.lagom.scaladsl.server.LagomServer
import com.lightbend.lagom.spi.persistence.OffsetStore
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

class ScaladslRegisterTopicProducers(lagomServer: LagomServer, topicFactory: TopicFactory,
                                     info: ServiceInfo, actorSystem: ActorSystem, offsetStore: OffsetStore,
                                     serviceLocator: ServiceLocator)(implicit ec: ExecutionContext, mat: Materializer) {

  private val log = LoggerFactory.getLogger(classOf[ScaladslRegisterTopicProducers])
  private val kafkaConfig = KafkaConfig(actorSystem.settings.config)

  // Goes through the services' descriptors and publishes the streams registered in
  // each of the service's topic method implementation.
  for {
    service <- lagomServer.serviceBindings
    tc <- service.descriptor.topics
    topicCall = tc.asInstanceOf[TopicCall[Any]]
  } {
    topicCall.topicHolder match {
      case holder: ScalaMethodTopic[Any] =>
        val topicProducer = holder.method.invoke(service.service)
        val topicId = topicCall.topicId

        topicFactory.create(topicCall) match {
          case topicImpl: ScaladslKafkaTopic[Any] =>

            topicProducer match {
              case tagged: TaggedOffsetTopicProducer[Any, _] =>

                val tags = tagged.tags

                val eventStreamFactory: (String, Offset) => Source[(Any, Offset), _] = { (tag, offset) =>
                  tags.find(_.tag == tag) match {
                    case Some(aggregateTag) => tagged.readSideStream(aggregateTag, offset)
                    case None               => throw new RuntimeException("Unknown tag: " + tag)
                  }
                }

                val partitionKeyStrategy: Option[Any => String] = {
                  topicCall.properties.get(KafkaProperties.partitionKeyStrategy).map { pks => message =>
                    pks.computePartitionKey(message)
                  }
                }

                Producer.startTaggedOffsetProducer(actorSystem, tags.map(_.tag), kafkaConfig, serviceLocator.locate,
                  topicId.name, eventStreamFactory, partitionKeyStrategy,
                  new ScaladslKafkaSerializer(topicCall.messageSerializer.serializerForRequest),
                  offsetStore)
              case other => log.warn {
                s"Unknown topic producer ${other.getClass.getName}. " +
                  s"This will likely result in no events published to topic ${topicId.name} by service ${info.serviceName}."
              }
            }

          case otherTopicImpl => log.warn {
            s"Expected Topic type ${classOf[ScaladslKafkaTopic[_]].getName}, but found incompatible type ${otherTopicImpl.getClass.getName}." +
              s"This will likely result in no events published to topic ${topicId.name} by service ${info.serviceName}."
          }
        }

      case other =>
        log.error {
          s"Cannot plug publisher source for topic ${topicCall.topicId}. " +
            s"Reason was that it was expected a topicHolder of type ${classOf[ScalaMethodTopic[_]]}, " +
            s"but ${other.getClass} was found instead."
        }
    }
  }

}
