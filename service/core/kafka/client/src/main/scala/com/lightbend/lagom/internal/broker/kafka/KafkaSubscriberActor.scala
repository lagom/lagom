/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import akka.Done
import akka.actor.{ Actor, ActorLogging, Props, Status }
import akka.kafka.ConsumerMessage.{ CommittableOffset, CommittableOffsetBatch }
import akka.kafka.scaladsl.{ Consumer => ReactiveConsumer }
import akka.kafka.{ AutoSubscription, ConsumerSettings }
import akka.pattern.pipe
import akka.stream.scaladsl.{ Flow, GraphDSL, Keep, Sink, Source, Unzip, Zip }
import akka.stream.{ FlowShape, KillSwitch, KillSwitches, Materializer }

import scala.concurrent.{ ExecutionContext, Promise }

private[lagom] class KafkaSubscriberActor[Message](
  consumerConfig: ConsumerConfig, topicId: String, flow: Flow[Message, Done, _],
  consumerSettings: ConsumerSettings[String, Message], subscription: AutoSubscription,
  streamCompleted: Promise[Done]
)(implicit mat: Materializer, ec: ExecutionContext) extends Actor with ActorLogging {

  /** Switch used to terminate the on-going Kafka publishing stream when this actor fails.*/
  private var shutdown: Option[KillSwitch] = None

  override def preStart(): Unit = {
    val (killSwitch, streamDone) =
      atLeastOnce(flow)
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.both)
        .run()

    shutdown = Some(killSwitch)
    streamDone pipeTo self
  }

  override def postStop(): Unit = {
    shutdown.foreach(_.shutdown())
  }

  override def receive: Actor.Receive = {
    case Status.Failure(e) =>
      throw e

    case Done =>
      log.info("Kafka subscriber stream for topic {} was completed.", topicId)
      streamCompleted.success(Done)
      context.stop(self)
  }

  private def atLeastOnce(flow: Flow[Message, Done, _]): Source[Done, _] = {
    // Creating a Source of pair where the first element is a reactive-kafka committable offset,
    // and the second it's the actual message. Then, the source of pair is splitted into
    // two streams, so that the `flow` passed in argument can be applied to the underlying message.
    // After having applied the `flow`, the two streams are combined back and the processed message's
    // offset is committed to Kafka.
    val pairedCommittableSource = ReactiveConsumer.committableSource(consumerSettings, subscription)
      .map(committableMessage => (committableMessage.committableOffset, committableMessage.record.value))

    val committOffsetFlow = Flow.fromGraph(GraphDSL.create(flow) { implicit builder => flow =>
      import GraphDSL.Implicits._
      val unzip = builder.add(Unzip[CommittableOffset, Message])
      val zip = builder.add(Zip[CommittableOffset, Done])
      val committer = {
        val commitFlow = Flow[(CommittableOffset, Done)]
          .groupedWithin(consumerConfig.batchingSize, consumerConfig.batchingInterval)
          .map(group => group.foldLeft(CommittableOffsetBatch.empty) { (batch, elem) => batch.updated(elem._1) })
          // parallelism set to 3 for no good reason other than because the akka team has seen good throughput with this value
          .mapAsync(parallelism = 3)(_.commitScaladsl())
        builder.add(commitFlow)
      }

      unzip.out0 ~> zip.in0
      unzip.out1 ~> flow ~> zip.in1
      zip.out ~> committer.in

      FlowShape(unzip.in, committer.out)
    })

    pairedCommittableSource.via(committOffsetFlow)
  }
}

object KafkaSubscriberActor {
  def props[Message](
    consumerConfig: ConsumerConfig, topicId: String, flow: Flow[Message, Done, _],
    consumerSettings: ConsumerSettings[String, Message], subscription: AutoSubscription,
    streamCompleted: Promise[Done]
  )(implicit mat: Materializer, ec: ExecutionContext) =
    Props(new KafkaSubscriberActor[Message](consumerConfig, topicId, flow, consumerSettings, subscription, streamCompleted))
}
