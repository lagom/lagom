/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.broker.kafka

import java.net.URI

import akka.actor.ActorSystem
import akka.persistence.query.{ Offset => AkkaOffset }
import akka.stream.Materializer
import akka.stream.scaladsl._
import com.lightbend.lagom.internal.projection.ProjectionRegistry
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.spi.persistence.OffsetStore
import org.apache.kafka.common.serialization.Serializer

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * A Producer for publishing messages in Kafka using the Alpakka Kafka API.
 */
private[lagom] object Producer {
  def startTaggedOffsetProducer[Message](
      system: ActorSystem,
      tags: immutable.Seq[String],
      kafkaConfig: KafkaConfig,
      locateService: String => Future[Seq[URI]],
      topicId: String,
      eventStreamFactory: (String, AkkaOffset) => Source[(Message, AkkaOffset), _],
      partitionKeyStrategy: Option[Message => String],
      serializer: Serializer[Message],
      offsetStore: OffsetStore,
      projectionRegistry: ProjectionRegistry
  )(implicit mat: Materializer, ec: ExecutionContext): Unit = {
    val projectionName = s"kafkaProducer-$topicId"

    val producerConfig = ProducerConfig(system.settings.config)
    val topicProducerProps = (coordinates: WorkerCoordinates) =>
      TopicProducerActor.props(
        coordinates,
        kafkaConfig,
        producerConfig,
        locateService,
        topicId,
        eventStreamFactory,
        partitionKeyStrategy,
        serializer,
        offsetStore
      )

    val entityIds = tags.toSet

    projectionRegistry.registerProjection(
      projectionName,
      entityIds,
      topicProducerProps,
      producerConfig.role
    )
  }
}
