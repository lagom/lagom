/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.mb;

import akka.Done;
import akka.NotUsed;
import akka.japi.Pair;

import javax.inject.Inject;

import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;

public class HelloServiceImpl implements HelloService {

  private final PersistentEntityRegistry persistentEntityRegistry;

  @Inject
  public HelloServiceImpl(PersistentEntityRegistry persistentEntityRegistry) {
    this.persistentEntityRegistry = persistentEntityRegistry;
  }

  @Override
  // #implement-topic
  public Topic<GreetingMessage> greetingsTopic() {
    return TopicProducer.singleStreamWithOffset(
        offset -> {
          return persistentEntityRegistry
              .eventStream(HelloEventTag.INSTANCE, offset)
              .map(this::convertEvent);
        });
  }
  // #implement-topic

  private Pair<GreetingMessage, Offset> convertEvent(Pair<HelloEvent, Offset> pair) {
    return new Pair<>(
        new GreetingMessage(pair.first().getId(), pair.first().getMessage()), pair.second());
  }

  @Override
  public com.lightbend.lagom.javadsl.api.ServiceCall<NotUsed, String> hello(String id) {
    throw new UnsupportedOperationException("Missing implementation");
  }

  @Override
  public com.lightbend.lagom.javadsl.api.ServiceCall<GreetingMessage, Done> useGreeting(String id) {
    throw new UnsupportedOperationException("Missing implementation");
  }
}
