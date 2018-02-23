/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import java.net.URI

import akka.Done
import akka.actor.{ Actor, ActorLogging, Props, Status }
import akka.kafka.ConsumerMessage.{ CommittableOffset, CommittableOffsetBatch }
import akka.kafka.scaladsl.{ Consumer => ReactiveConsumer }
import akka.kafka.{ AutoSubscription, ConsumerSettings }
import akka.pattern.pipe
import akka.stream.scaladsl.{ Flow, GraphDSL, Keep, Sink, Source, Unzip, Zip }
import akka.stream._
import com.lightbend.lagom.internal.api.UriUtils
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.{ ExecutionContext, Future, Promise }

private[lagom] class KafkaSubscriberActor[Payload, SubscriberPayload](
  kafkaConfig: KafkaConfig, consumerConfig: ConsumerConfig,
  locateService: String => Future[Seq[URI]], topicId: String, flow: Flow[SubscriberPayload, Done, _],
  consumerSettings: ConsumerSettings[String, Payload], subscription: AutoSubscription,
  streamCompleted: Promise[Done], transform: ConsumerRecord[String, Payload] => SubscriberPayload
)(implicit mat: Materializer, ec: ExecutionContext) extends Actor with ActorLogging {

  /** Switch used to terminate the on-going Kafka publishing stream when this actor fails.*/
  private var shutdown: Option[KillSwitch] = None

  override def preStart(): Unit = {
    kafkaConfig.serviceName match {
      case Some(name) =>
        log.debug("Looking up Kafka service from service locator with name [{}] for at least once source", name)
        locateService(name).map {
          case Nil  => None
          case uris => Some(UriUtils.hostAndPorts(uris))
        } pipeTo self
        context.become(locatingService(name))
      case None =>
        run(None)
    }
  }

  override def postStop(): Unit = {
    shutdown.foreach(_.shutdown())
  }

  private def locatingService(name: String): Receive = {
    case Status.Failure(e) =>
      log.error(s"Error locating Kafka service named [$name]", e)
      throw e

    case None =>
      log.error("Unable to locate Kafka service named [{}]", name)
      context.stop(self)

    case Some(uris: String) =>
      log.debug("Kafka service [{}] located at URI [{}] for subscriber of [{}]", name, uris, topicId)
      run(Some(uris))
  }

  private def running: Receive = {
    case Status.Failure(e) =>
      log.error("Topic subscription interrupted due to failure: [{}]", e)
      throw e

    case Done =>
      log.info("Kafka subscriber stream for topic {} was completed.", topicId)
      streamCompleted.success(Done)
      context.stop(self)
  }

  override def receive = PartialFunction.empty

  private def run(uri: Option[String]) = {
    val (killSwitch, streamDone) =
      atLeastOnce(uri)
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.both)
        .run()

    shutdown = Some(killSwitch)
    streamDone pipeTo self
    context.become(running)
  }

  private def atLeastOnce(serviceLocatorUris: Option[String]): Source[Done, _] = {
    // Creating a Source of pair where the first element is a reactive-kafka committable offset,
    // and the second it's the actual message. Then, the source of pair is splitted into
    // two streams, so that the `flow` passed in argument can be applied to the underlying message.
    // After having applied the `flow`, the two streams are combined back and the processed message's
    // offset is committed to Kafka.
    val consumerSettingsWithUri = serviceLocatorUris match {
      case Some(uris) => consumerSettings.withBootstrapServers(uris)
      case None       => consumerSettings
    }
    val pairedCommittableSource = ReactiveConsumer.committableSource(consumerSettingsWithUri, subscription)
      .map(committableMessage => (committableMessage.committableOffset, transform(committableMessage.record)))

    val committOffsetFlow = Flow.fromGraph(GraphDSL.create(flow) { implicit builder => flow =>
      import GraphDSL.Implicits._
      val unzip = builder.add(Unzip[CommittableOffset, SubscriberPayload])
      val zip = builder.add(Zip[CommittableOffset, Done])
      val committer = {
        val commitFlow = Flow[(CommittableOffset, Done)]
          .groupedWithin(consumerConfig.batchingSize, consumerConfig.batchingInterval)
          .map(group => group.foldLeft(CommittableOffsetBatch.empty) { (batch, elem) => batch.updated(elem._1) })
          // parallelism set to 3 for no good reason other than because the akka team has seen good throughput with this value
          .mapAsync(parallelism = 3)(_.commitScaladsl())
        builder.add(commitFlow)
      }
      // To allow the user flow to do its own batching, the offset side of the flow needs to effectively buffer
      // infinitely to give full control of backpressure to the user side of the flow.
      val offsetBuffer = Flow[CommittableOffset].buffer(consumerConfig.offsetBuffer, OverflowStrategy.backpressure)

      unzip.out0 ~> offsetBuffer ~> zip.in0
      unzip.out1 ~> flow ~> zip.in1
      zip.out ~> committer.in

      FlowShape(unzip.in, committer.out)
    })

    pairedCommittableSource.via(committOffsetFlow)
  }
}

object KafkaSubscriberActor {
  def props[Payload, SubscriberPayload](
    kafkaConfig:      KafkaConfig,
    consumerConfig:   ConsumerConfig,
    locateService:    String => Future[Seq[URI]],
    topicId:          String,
    flow:             Flow[SubscriberPayload, Done, _],
    consumerSettings: ConsumerSettings[String, Payload],
    subscription:     AutoSubscription,
    streamCompleted:  Promise[Done],
    transform:        ConsumerRecord[String, Payload] => SubscriberPayload
  )(implicit mat: Materializer, ec: ExecutionContext) =
    Props(
      new KafkaSubscriberActor[Payload, SubscriberPayload](
        kafkaConfig,
        consumerConfig,
        locateService,
        topicId,
        flow,
        consumerSettings,
        subscription,
        streamCompleted,
        transform
      )
    )
}
