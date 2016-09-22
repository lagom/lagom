package docs.mb;


import akka.Done;
import akka.NotUsed;
import akka.japi.Pair;

import javax.inject.Inject;

import com.lightbend.lagom.javadsl.api.*;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.broker.kafka.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class HelloServiceImpl implements HelloService {

    //#inject-topic-producer
    private final TopicProducer topicProducer;
    private final PersistentEntityRegistry persistentEntityRegistry;

    public HelloServiceImpl(TopicProducer topicProducer, PersistentEntityRegistry persistentEntityRegistry) {
        this.topicProducer = topicProducer;
        this.persistentEntityRegistry = persistentEntityRegistry;
    }
    //#inject-topic-producer

    //#implement-topic
    public Topic<GreetingMessage> greetingsTopic() {
        return topicProducer.singletonAtLeastOnce(offset -> {
            return persistentEntityRegistry
                .eventStream(HelloEventTag.INSTANCE, offset)
                .map(this::convertEvent);
        });
    }
    //#implement-topic

    private Pair<GreetingMessage, Offset> convertEvent(Pair<HelloEvent, Offset> pair) {
      return new Pair<>(GreetingMessage.of(pair.first().getMessage()), pair.second());  
    }

    public ServiceCall<NotUsed, String> hello(String id) {
      throw new UnsupportedOperationException("Missing implementation");
    }

    public ServiceCall<GreetingMessage, Done> useGreeting(String id) {
      throw new UnsupportedOperationException("Missing implementation");
    }
}
