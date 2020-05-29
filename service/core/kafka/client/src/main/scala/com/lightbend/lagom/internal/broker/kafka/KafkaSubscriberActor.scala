/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.broker.kafka

import akka.Done
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.CoordinatedShutdown
import akka.actor.CoordinatedShutdown.PhaseServiceUnbind
import akka.actor.Props
import akka.actor.Status
import akka.kafka.AutoSubscription
import akka.kafka.ConsumerMessage.CommittableOffset
import akka.kafka.ConsumerSettings
import akka.kafka.scaladsl.Committer
import akka.kafka.scaladsl.Consumer
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.pattern.pipe
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Unzip
import akka.stream.scaladsl.Zip
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.Duration

private[lagom] class KafkaSubscriberActor[Payload, SubscriberPayload](
    consumerConfig: ConsumerConfig,
    topicId: String,
    flow: Flow[SubscriberPayload, Done, _],
    consumerSettings: ConsumerSettings[String, Payload],
    subscription: AutoSubscription,
    streamCompleted: Promise[Done],
    transform: ConsumerRecord[String, Payload] => SubscriberPayload
)(implicit mat: Materializer, ec: ExecutionContext)
    extends Actor
    with ActorLogging {

  override def preStart(): Unit = {
    val drainingControl: DrainingControl[Done] =
      atLeastOnce()
        .toMat(Committer.sink(consumerConfig.committerSettings))(DrainingControl.apply[Done])
        .run()

    CoordinatedShutdown(context.system).addTask(PhaseServiceUnbind, s"stop-$topicId-subscriber") { () =>
      drainingControl.drainAndShutdown()
    }

    val streamDone = drainingControl.streamCompletion
    streamDone.pipeTo(self)
    context.become(running)
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

  private def atLeastOnce(): Source[CommittableOffset, Consumer.Control] = {
    // Creating a Source of pair where the first element is an Alpakka Kafka committable offset,
    // and the second it's the actual message. Then, the source of the pair is split into
    // two streams, so that the `flow` passed in argument can be applied to the underlying message.
    // After having applied the `flow`, the two streams are combined back and the processed message's
    // offset is committed to Kafka.
    val pairedCommittableSource = Consumer
      .committableSource(consumerSettings.withStopTimeout(Duration.Zero), subscription)
      .map(msg => (msg.committableOffset, transform(msg.record)))

    val committableOffsetFlow = Flow.fromGraph(GraphDSL.create(flow) { implicit builder => flow =>
      import GraphDSL.Implicits._
      val unzip  = builder.add(Unzip[CommittableOffset, SubscriberPayload])
      val zip    = builder.add(Zip[CommittableOffset, Done])
      val offset = builder.add(Flow[(CommittableOffset, Done)].map(_._1))

      // To allow the user flow to do its own batching, the offset side of the flow needs to effectively buffer
      // infinitely to give full control of backpressure to the user side of the flow.
      val offsetBuffer = Flow[CommittableOffset].buffer(consumerConfig.offsetBuffer, OverflowStrategy.backpressure)

      unzip.out0 ~> offsetBuffer ~> zip.in0
      unzip.out1 ~> flow ~> zip.in1
      zip.out ~> offset.in

      FlowShape(unzip.in, offset.out)
    })

    pairedCommittableSource.via(committableOffsetFlow)
  }
}

object KafkaSubscriberActor {
  def props[Payload, SubscriberPayload](
      consumerConfig: ConsumerConfig,
      topicId: String,
      flow: Flow[SubscriberPayload, Done, _],
      consumerSettings: ConsumerSettings[String, Payload],
      subscription: AutoSubscription,
      streamCompleted: Promise[Done],
      transform: ConsumerRecord[String, Payload] => SubscriberPayload
  )(implicit mat: Materializer, ec: ExecutionContext) =
    Props(
      new KafkaSubscriberActor[Payload, SubscriberPayload](
        consumerConfig,
        topicId,
        flow,
        consumerSettings,
        subscription,
        streamCompleted,
        transform
      )
    )
}
