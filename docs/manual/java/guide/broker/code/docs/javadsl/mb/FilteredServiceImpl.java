/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.mb;

import akka.Done;
import akka.NotUsed;
import akka.japi.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;

import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;

import docs.javadsl.mb.HelloEvent.AbstractGreetingMessageChanged;

public class FilteredServiceImpl extends HelloServiceImpl {

  private final PersistentEntityRegistry persistentEntityRegistry;

  @Inject
  public FilteredServiceImpl(PersistentEntityRegistry persistentEntityRegistry) {
    super(persistentEntityRegistry);
    this.persistentEntityRegistry = persistentEntityRegistry;
  }

  @Override
  // #filter-events
  public Topic<GreetingMessage> greetingsTopic() {
    return TopicProducer.singleStreamWithOffset(
        offset -> {
          return persistentEntityRegistry
              .eventStream(HelloEventTag.INSTANCE, offset)
              .mapConcat(this::filterHelloGreetings);
        });
  }

  private List<Pair<GreetingMessage, Offset>> filterHelloGreetings(Pair<HelloEvent, Offset> pair) {
    if (pair.first() instanceof AbstractGreetingMessageChanged) {
      AbstractGreetingMessageChanged msg = (AbstractGreetingMessageChanged) pair.first();
      // Only publish greetings where the message is "Hello".
      if (msg.getMessage().equals("Hello")) {
        return Arrays.asList(convertEvent(pair));
      }
    }
    return Collections.emptyList();
  }
  // #filter-events

  private Pair<GreetingMessage, Offset> convertEvent(Pair<HelloEvent, Offset> pair) {
    return new Pair<>(
        new GreetingMessage(pair.first().getId(), pair.first().getMessage()), pair.second());
  }
}
