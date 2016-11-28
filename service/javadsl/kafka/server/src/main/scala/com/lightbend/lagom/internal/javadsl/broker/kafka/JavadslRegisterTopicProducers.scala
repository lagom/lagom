/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.broker.kafka

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import org.slf4j.LoggerFactory
import com.lightbend.lagom.javadsl.api.ServiceInfo
import akka.stream.Materializer
import javax.inject.Inject

import akka.actor.ActorSystem
import akka.persistence.query.Offset
import akka.stream.scaladsl.Source
import com.lightbend.lagom.internal.broker.TaggedOffsetTopicProducer
import com.lightbend.lagom.internal.broker.kafka.{ KafkaConfig, Producer }
import com.lightbend.lagom.internal.javadsl.api.MethodTopicHolder
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory
import com.lightbend.lagom.internal.javadsl.persistence.OffsetAdapter
import com.lightbend.lagom.internal.javadsl.server.ResolvedServices
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.broker.kafka.KafkaProperties
import com.lightbend.lagom.spi.persistence.OffsetStore
import scala.collection.immutable

class JavadslRegisterTopicProducers @Inject() (resolvedServices: ResolvedServices, topicFactory: TopicFactory,
                                               info: ServiceInfo, actorSystem: ActorSystem, offsetStore: OffsetStore)(implicit ec: ExecutionContext, mat: Materializer) {

  private val log = LoggerFactory.getLogger(classOf[JavadslRegisterTopicProducers])
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
          case topicImpl: JavadslKafkaTopic[AnyRef] =>

            topicProducer match {
              case tagged: TaggedOffsetTopicProducer[AnyRef, _] =>

                val tags = tagged.tags.asScala.to[immutable.Seq]

                val eventStreamFactory: (String, Offset) => Source[(AnyRef, Offset), _] = { (tag, offset) =>
                  tags.find(_.tag == tag) match {
                    case Some(aggregateTag) => tagged.readSideStream(
                      aggregateTag,
                      OffsetAdapter.offsetToDslOffset(offset)
                    ).asScala.map { pair =>
                        pair.first -> OffsetAdapter.dslOffsetToOffset(pair.second)
                      }
                    case None => throw new RuntimeException("Unknown tag: " + tag)
                  }
                }

                val partitionKeyStrategy: Option[AnyRef => String] = {
                  val javaPKS = topicCall.properties().getValueOf(KafkaProperties.partitionKeyStrategy())
                  if (javaPKS != null) {
                    Some(message => javaPKS.computePartitionKey(message))
                  } else None
                }

                Producer.startTaggedOffsetProducer(actorSystem, tags.map(_.tag), kafkaConfig, topicId.value(),
                  eventStreamFactory, partitionKeyStrategy,
                  new JavadslKafkaSerializer(topicCall.messageSerializer().serializerForRequest()),
                  offsetStore)
              case other => log.warn {
                s"Unknown topic producer ${other.getClass.getName}. " +
                  s"This will likely result in no events published to topic ${topicId.value} by service ${info.serviceName}."
              }
            }

          case otherTopicImpl => log.warn {
            s"Expected Topic type ${classOf[JavadslKafkaTopic[_]].getName}, but found incompatible type ${otherTopicImpl.getClass.getName}." +
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