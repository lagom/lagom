/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.mb;

import akka.NotUsed;
import akka.Done;

// #hello-service
import com.lightbend.lagom.javadsl.api.*;
import com.lightbend.lagom.javadsl.api.broker.Topic;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface HelloService extends Service {
  String GREETINGS_TOPIC = "greetings";

  @Override
  default Descriptor descriptor() {
    return named("helloservice")
        .withCalls(
            pathCall("/api/hello/:id", this::hello), pathCall("/api/hello/:id", this::useGreeting))
        // here we declare the topic(s) this service will publish to
        .withTopics(topic(GREETINGS_TOPIC, this::greetingsTopic))
        .withAutoAcl(true);
  }
  // The topic handle
  Topic<GreetingMessage> greetingsTopic();

  ServiceCall<NotUsed, String> hello(String id);

  ServiceCall<GreetingMessage, Done> useGreeting(String id);
}
// #hello-service
