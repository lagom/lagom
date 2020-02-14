/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.broker.kafka

import java.net.URI

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.query.EventEnvelope
import akka.persistence.query.{ Offset => AkkaOffset }
import akka.stream.Materializer
import akka.stream.javadsl.Flow
import akka.stream.scaladsl.Source
import com.lightbend.lagom.internal.broker.DelegatedTopicProducer
import com.lightbend.lagom.internal.broker.TaggedInternalTopic
import com.lightbend.lagom.internal.broker.TaggedOffsetTopicProducer
import com.lightbend.lagom.internal.broker.kafka.ClassicLagomEventStreamFactory
import com.lightbend.lagom.internal.broker.kafka.DelegatedEventStreamFactory
import com.lightbend.lagom.internal.broker.kafka.EventStreamFactory
import com.lightbend.lagom.internal.broker.kafka.KafkaConfig
import com.lightbend.lagom.internal.broker.kafka.Producer
import com.lightbend.lagom.internal.javadsl.api.MethodTopicHolder
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory
import com.lightbend.lagom.internal.javadsl.persistence.AbstractPersistentEntityRegistry
import com.lightbend.lagom.internal.javadsl.persistence.OffsetAdapter
import com.lightbend.lagom.internal.javadsl.server.ResolvedServices
import com.lightbend.lagom.internal.projection.ProjectionRegistry
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.lightbend.lagom.javadsl.api.broker.kafka.KafkaProperties
import com.lightbend.lagom.javadsl.persistence.AggregateEvent
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag
import com.lightbend.lagom.javadsl.persistence.Offset
import com.lightbend.lagom.spi.persistence.OffsetStore
import javax.inject.Inject
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class JavadslRegisterTopicProducers[BrokerMessage, Event <: AggregateEvent[Event]] @Inject() (
    resolvedServices: ResolvedServices,
    topicFactory: TopicFactory,
    info: ServiceInfo,
    actorSystem: ActorSystem,
    offsetStore: OffsetStore,
    serviceLocator: ServiceLocator,
    projectionRegistryImpl: ProjectionRegistry
)(implicit ec: ExecutionContext, mat: Materializer) {
  private val log         = LoggerFactory.getLogger(classOf[JavadslRegisterTopicProducers[_, _]])
  private val kafkaConfig = KafkaConfig(actorSystem.settings.config)

  // Goes through the services' descriptors and publishes the streams registered in
  // each of the service's topic method implementation.
  for {
    service <- resolvedServices.services
    tc      <- service.descriptor.topicCalls().asScala
    topicCall = tc.asInstanceOf[TopicCall[BrokerMessage]]
  } {
    topicCall.topicHolder match {
      case holder: MethodTopicHolder =>
        val topicProducer = holder.create(service.service)
        val topicId       = topicCall.topicId

        topicFactory.create(topicCall) match {
          case topicImpl: JavadslKafkaTopic[BrokerMessage] =>
            topicProducer match {
              case tagged: TaggedInternalTopic[BrokerMessage, Event] =>
                val tags = tagged.tags.asScala.toIndexedSeq

                val eventStreamFactory: EventStreamFactory[BrokerMessage] =
                  tagged match {
                    case producer: DelegatedTopicProducer[BrokerMessage, Event] =>
                      val sourceFactory: (String, AkkaOffset) => Source[EventEnvelope, NotUsed] = (tag, offset) =>
                        tags.find(_.tag == tag) match {
                          case Some(aggregateTag) =>
                            val lagomOffset = OffsetAdapter.offsetToDslOffset(offset)
                            producer.persistentEntityRegistry
                              .eventEnvelopeStream(aggregateTag, lagomOffset)
                              .asScala
                          case None => throw new RuntimeException("Unknown tag: " + tag)
                        }

                      val userFlow: Flow[EventEnvelope, (BrokerMessage, AkkaOffset), NotUsed] = Flow
                        .create[EventEnvelope]
                        .map(AbstractPersistentEntityRegistry.toStreamElement[Event])
                        .via(producer.userFlow)
                        .map { pair =>
                          pair.first -> OffsetAdapter.dslOffsetToOffset(pair.second)
                        }

                      DelegatedEventStreamFactory(
                        sourceFactory,
                        userFlow.asScala
                      )
                    case producer: TaggedOffsetTopicProducer[BrokerMessage, Event] =>
                      ClassicLagomEventStreamFactory((tag, offset) =>
                        tags.find(_.tag == tag) match {
                          case Some(aggregateTag) =>
                            val lagomOffset = OffsetAdapter.offsetToDslOffset(offset)
                            producer
                              .readSideStream(aggregateTag, lagomOffset)
                              .map { pair =>
                                pair.first -> OffsetAdapter.dslOffsetToOffset(pair.second)
                              }
                              .asScala
                          case None => throw new RuntimeException("Unknown tag: " + tag)
                        }
                      )
                  }

                val shardEntityIds = tagged match {
                  case producer: DelegatedTopicProducer[_, _]    => producer.clusterShardEntityIds.asScala.toList
                  case producer: TaggedOffsetTopicProducer[_, _] => producer.tags.asScala.map(_.tag).toList
                }

                val partitionKeyStrategy: Option[BrokerMessage => String] = {
                  val javaPKS = topicCall.properties().getValueOf(KafkaProperties.partitionKeyStrategy())
                  if (javaPKS != null) {
                    Some(message => javaPKS.computePartitionKey(message))
                  } else None
                }

                Producer.startTaggedOffsetProducer(
                  actorSystem,
                  shardEntityIds,
                  kafkaConfig,
                  locateService,
                  topicId.value(),
                  eventStreamFactory,
                  partitionKeyStrategy,
                  new JavadslKafkaSerializer(topicCall.messageSerializer().serializerForRequest()),
                  offsetStore,
                  projectionRegistryImpl
                )

              case other =>
                log.warn {
                  s"Unknown topic producer ${other.getClass.getName}. " +
                    s"This will likely result in no events published to topic ${topicId.value} by service ${info.serviceName}."
                }
            }

          case otherTopicImpl =>
            log.warn {
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

  private def locateService(name: String): Future[Seq[URI]] =
    serviceLocator.locateAll(name).toScala.map(_.asScala.toIndexedSeq)
}
