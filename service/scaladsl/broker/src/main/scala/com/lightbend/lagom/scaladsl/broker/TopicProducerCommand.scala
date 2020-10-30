/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.broker

import java.util.Objects;
import akka.persistence.query.Offset

sealed trait TopicProducerCommand[Message]

object TopicProducerCommand {
  final class EmitAndCommit[Message](val message: Message, val offset: Offset) extends TopicProducerCommand[Message] {
    override def equals(that: Any): Boolean = that match {
      case command: EmitAndCommit[Message] => message.equals(command.message) && offset.equals(command.offset)
      case _                               => false
    }

    override def hashCode(): Int = Objects.hash(message.asInstanceOf[AnyRef], offset)
  }

  final class Commit[Message](val offset: Offset) extends TopicProducerCommand[Message] {
    override def equals(that: Any): Boolean = that match {
      case command: Commit[Message] => offset.equals(command.offset)
      case _                        => false
    }

    override def hashCode(): Int = Objects.hash(offset)
  }
}
