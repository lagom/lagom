/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.testkit;

import com.lightbend.lagom.javadsl.api.broker.Message;
import com.lightbend.lagom.javadsl.testkit.services.PublishService;
import com.lightbend.lagom.javadsl.testkit.services.PublishEvent;
import com.lightbend.lagom.javadsl.testkit.services.PublishModule;
import com.lightbend.lagom.javadsl.testkit.services.PublishServiceImpl;
import org.junit.Test;
import akka.stream.javadsl.Source;
import akka.stream.testkit.javadsl.TestSink;
import akka.stream.testkit.TestSubscriber;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;

public class PublishServiceTest {

  private final Setup setup = defaultSetup().configureBuilder(b -> b.bindings(new PublishModule()));

  // #topic-test-publishing-into-a-topic
  @Test
  public void shouldEmitGreetingsMessageWhenHelloEntityEmitsAnEvent() {
    withServer(
        setup,
        server -> {
          PublishService client = server.client(PublishService.class);
          Source<PublishEvent, ?> source = client.messageTopic().subscribe().atMostOnceSource();

          // use akka stream testkit
          TestSubscriber.Probe<PublishEvent> probe =
              source.runWith(TestSink.probe(server.system()), server.materializer());

          PublishEvent actual = probe.request(1).expectNext();
          assertEquals(new PublishEvent(23), actual);
        });
  }
  // #topic-test-publishing-into-a-topic

  @Test
  public void shouldEmitGreetingsMessageWithMetadataWhenHelloEntityEmitsAnEvent() {
    withServer(
        setup,
        server -> {
          PublishService client = server.client(PublishService.class);
          Source<Message<PublishEvent>, ?> source =
              client.messageWithMetadataTopic().subscribe().withMetadata().atMostOnceSource();

          // use akka stream testkit
          TestSubscriber.Probe<Message<PublishEvent>> probe =
              source.runWith(TestSink.probe(server.system()), server.materializer());

          Message<PublishEvent> actual = probe.request(1).expectNext();
          PublishEvent payload = actual.getPayload();
          assertEquals(new PublishEvent(23), payload);
          assertEquals(Optional.of("value-23"), actual.get(PublishServiceImpl.METADATA_KEY));
        });
  }
}
