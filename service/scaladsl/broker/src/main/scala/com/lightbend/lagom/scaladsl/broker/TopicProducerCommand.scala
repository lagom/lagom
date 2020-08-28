/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.broker

import akka.persistence.query.Offset

sealed trait TopicProducerCommand[Message]

object TopicProducerCommand {
  case class EmitAndCommit[Message](message: Message, offset: Offset) extends TopicProducerCommand[Message]
  case class Commit[Message](offset: Offset)                          extends TopicProducerCommand[Message]
}
