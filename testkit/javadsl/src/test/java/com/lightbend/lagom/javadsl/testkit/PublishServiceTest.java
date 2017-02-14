/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit;

import com.lightbend.lagom.javadsl.testkit.services.PublishService;
import com.lightbend.lagom.javadsl.testkit.services.PublishEvent;
import com.lightbend.lagom.javadsl.testkit.services.PublishModule;
import org.junit.Test;
import akka.stream.javadsl.Source;
import akka.stream.testkit.javadsl.TestSink;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.TestSubscriber.Probe;
import static org.junit.Assert.assertEquals;
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;

public class PublishServiceTest {

    private Setup setup = defaultSetup().configureBuilder(b ->
            b.bindings(new PublishModule())
    );

    //#topic-test-publishing-into-a-topic
    @Test
    public void shouldEmitGreetingsMessageWhenHelloEntityEmitsEnEvent() {
        withServer(setup, server -> {
            PublishService client = server.client(PublishService.class);
            Source<PublishEvent, ?> source = 
                  client.messageTopic().subscribe().atMostOnceSource();

            // use akka stream testkit
            TestSubscriber.Probe<PublishEvent> probe =
                  source.runWith(
                        TestSink.probe(server.system()), server.materializer()
                  );

            PublishEvent actual = probe.request(1).expectNext();
            assertEquals(new PublishEvent(23), actual);
        });
    }
    //#topic-test-publishing-into-a-topic

}
