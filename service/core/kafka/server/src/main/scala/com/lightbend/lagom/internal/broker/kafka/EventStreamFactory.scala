package com.lightbend.lagom.internal.broker.kafka

import akka.NotUsed
import akka.persistence.query.EventEnvelope
import akka.persistence.query.{ Offset => AkkaOffset }
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source

sealed trait EventStreamFactory[BrokerMessage]

case class ClassicLagomEventStreamFactory[BrokerMessage](
    factory: (String, AkkaOffset) => Source[(BrokerMessage, AkkaOffset), _]
) extends EventStreamFactory[BrokerMessage]

case class DelegatedEventStreamFactory[BrokerMessage, Event](
    factory: (String, AkkaOffset) => Source[EventEnvelope, NotUsed],
    userFlow: Flow[EventEnvelope, (BrokerMessage, AkkaOffset), NotUsed]
) extends EventStreamFactory[BrokerMessage]
