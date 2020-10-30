/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.broker.kafka

import java.net.URI

import akka.actor.ActorSystem
import akka.persistence.query.{ Offset => AkkaOffset }
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.lightbend.lagom.internal.broker.TaggedOffsetTopicProducer
import com.lightbend.lagom.internal.broker.kafka.InternalTopicProducerCommand
import com.lightbend.lagom.internal.broker.kafka.KafkaConfig
import com.lightbend.lagom.internal.broker.kafka.Producer
import com.lightbend.lagom.internal.javadsl.api.MethodTopicHolder
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory
import com.lightbend.lagom.internal.javadsl.persistence.OffsetAdapter
import com.lightbend.lagom.internal.javadsl.server.ResolvedServices
import com.lightbend.lagom.internal.projection.ProjectionRegistry
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.lightbend.lagom.javadsl.api.broker.kafka.KafkaProperties
import com.lightbend.lagom.javadsl.broker.TopicProducerCommand
import com.lightbend.lagom.spi.persistence.OffsetStore
import javax.inject.Inject
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class JavadslRegisterTopicProducers @Inject() (
    resolvedServices: ResolvedServices,
    topicFactory: TopicFactory,
    info: ServiceInfo,
    actorSystem: ActorSystem,
    offsetStore: OffsetStore,
    serviceLocator: ServiceLocator,
    projectionRegistryImpl: ProjectionRegistry
)(implicit ec: ExecutionContext, mat: Materializer) {
  private val log         = LoggerFactory.getLogger(classOf[JavadslRegisterTopicProducers])
  private val kafkaConfig = KafkaConfig(actorSystem.settings.config)

  // Goes through the services' descriptors and publishes the streams registered in
  // each of the service's topic method implementation.
  for {
    service <- resolvedServices.services
    tc      <- service.descriptor.topicCalls().asScala
    topicCall = tc.asInstanceOf[TopicCall[AnyRef]]
  } {
    topicCall.topicHolder match {
      case holder: MethodTopicHolder =>
        val topicProducer = holder.create(service.service)
        val topicId       = topicCall.topicId

        topicFactory.create(topicCall) match {
          case topicImpl: JavadslKafkaTopic[AnyRef] =>
            topicProducer match {
              case tagged: TaggedOffsetTopicProducer[AnyRef, _] =>
                val tags = tagged.tags.asScala.toIndexedSeq

                val eventStreamFactory: (String, AkkaOffset) => Source[InternalTopicProducerCommand[AnyRef], _] = {
                  (tag, offset) =>
                    tags.find(_.tag == tag) match {
                      case Some(aggregateTag) =>
                        tagged
                          .readSideStream(
                            aggregateTag,
                            OffsetAdapter.offsetToDslOffset(offset)
                          )
                          .asScala
                          .map(toInternalTopicProducerCommand)

                      case None => throw new RuntimeException("Unknown tag: " + tag)
                    }
                }

                val partitionKeyStrategy: Option[AnyRef => String] = {
                  val javaPKS = topicCall.properties().getValueOf(KafkaProperties.partitionKeyStrategy())
                  if (javaPKS != null) {
                    Some(message => javaPKS.computePartitionKey(message))
                  } else None
                }

                Producer.startTaggedOffsetProducer(
                  actorSystem,
                  tags.map(_.tag),
                  kafkaConfig,
                  locateService,
                  topicId.value(),
                  eventStreamFactory,
                  partitionKeyStrategy,
                  new JavadslKafkaSerializer(topicCall.messageSerializer().serializerForRequest()),
                  offsetStore,
                  projectionRegistryImpl
                )
              case null =>
                val message = s"Expected an instance of ${classOf[MethodTopicHolder]}, but 'null' was passed"
                log.error(message, new NullPointerException(message))

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

  private def toInternalTopicProducerCommand[Message](
      command: TopicProducerCommand[Message]
  ): InternalTopicProducerCommand[Message] =
    command match {
      case cmd: TopicProducerCommand.EmitAndCommit[Message] =>
        InternalTopicProducerCommand.EmitAndCommit(
          cmd.message(),
          OffsetAdapter.dslOffsetToOffset(cmd.offset())
        )
      case cmd: TopicProducerCommand.Commit[Message] =>
        InternalTopicProducerCommand.Commit(OffsetAdapter.dslOffsetToOffset(cmd.offset()))
    }
}
