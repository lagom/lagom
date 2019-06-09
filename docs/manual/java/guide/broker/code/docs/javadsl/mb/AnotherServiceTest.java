/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.mb;

import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import org.junit.Test;
import akka.Done;
import akka.NotUsed;

import javax.inject.Inject;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.lightbend.lagom.javadsl.testkit.ProducerStub;
import com.lightbend.lagom.javadsl.testkit.ProducerStubFactory;
import scala.concurrent.Await;
import scala.concurrent.duration.FiniteDuration;

// #topic-test-consuming-from-a-topic
public class AnotherServiceTest {

  // (1) creates a server using the Module for this service Module
  // and we override the config to use HelloServiceStub
  // implemented below.
  private Setup setup =
      defaultSetup()
          .configureBuilder(
              b ->
                  b.overrides(
                      bind(HelloService.class).to(HelloServiceStub.class),
                      bind(AnotherService.class).to(AnotherServiceImpl.class)));

  // (2) an instance of ProducerStub allows test code to inject
  // messages on the topic.
  private static ProducerStub<GreetingMessage> helloProducer;

  @Test
  public void shouldReceiveMessagesFromUpstream() {
    // (1)
    withServer(
        setup,
        server -> {
          GreetingMessage message = new GreetingMessage("someId", "Hi there!");

          AnotherService client = server.client(AnotherService.class);
          client.audit().invoke().toCompletableFuture().get(3, SECONDS);

          // (4) send a message in the topic
          helloProducer.send(message);

          // use a service client instance to interact with the service
          // and assert the message was processed as expected.
          // ...

          // You will probably need to wrap your assertion in an
          // `eventually()` clause so you can retry your assertion
          // since your invocation via the service client may arrive
          // before the message was consumed.

        });
  }

  // (1) Stub for the upstream Service
  static class HelloServiceStub implements HelloService {
    // (2) Receives a ProducerStubFactory that factors ProducerStubs
    @Inject
    HelloServiceStub(ProducerStubFactory producerFactory) {
      // (3) Create a stub to request a producer for a specific topic
      helloProducer = producerFactory.producer(GREETINGS_TOPIC);
    }

    @Override
    public Topic<GreetingMessage> greetingsTopic() {
      // (3) the upstream stub must return the topic bound to the producer stub
      return helloProducer.topic();
    }

    @Override
    public ServiceCall<NotUsed, String> hello(String id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ServiceCall<GreetingMessage, Done> useGreeting(String id) {
      throw new UnsupportedOperationException();
    }
  }
}
// #topic-test-consuming-from-a-topic
