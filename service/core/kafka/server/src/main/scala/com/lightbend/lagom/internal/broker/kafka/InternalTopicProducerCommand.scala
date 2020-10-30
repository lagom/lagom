/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.broker.kafka

import akka.persistence.query.Offset

sealed trait InternalTopicProducerCommand[Message] {
  def offset: Offset
  def withOffset(offset: Offset): InternalTopicProducerCommand[Message]
}

object InternalTopicProducerCommand {
  case class EmitAndCommit[Message](message: Message, override val offset: Offset)
      extends InternalTopicProducerCommand[Message] {
    override def withOffset(offset: Offset): EmitAndCommit[Message] = copy(offset = offset)
  }

  case class Commit[Message](override val offset: Offset) extends InternalTopicProducerCommand[Message] {
    override def withOffset(offset: Offset): Commit[Message] = copy(offset = offset)
  }
}
