/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.testkit

import akka.actor.{ Actor, ActorRef, Props }

import scala.collection.mutable

private[lagom] object TopicBufferActor {
  def props(): Props = Props(new TopicBufferActor())

  case class SubscribeToBuffer(groupId: String, actorRef: ActorRef)

}

private[lagom] class TopicBufferActor extends Actor {

  import TopicBufferActor._

  var downstreams = Map.empty[String, ActorRef]
  val buffer: mutable.Buffer[AnyRef] = mutable.Buffer.empty[AnyRef]

  override def receive: Receive = {
    case SubscribeToBuffer(groupId, ref) => {
      downstreams = downstreams + (groupId -> ref)
      buffer.foreach(msg => ref.tell(msg, ActorRef.noSender))
    }
    case message: AnyRef => {
      downstreams.values.foreach(ref => ref ! message)
      buffer append message
    }
  }
}

