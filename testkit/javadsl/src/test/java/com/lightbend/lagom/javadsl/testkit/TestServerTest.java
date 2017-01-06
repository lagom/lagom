/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit;

import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestServerTest {

    @Test
    public void testTestingTopics() {
        ServiceTest.withServer(ServiceTest.defaultSetup()
                        .configureBuilder(builder ->
                                builder.bindings(new TestTopicServiceModule())), server -> {

            Source<String, ?> source = server.client(TestTopicService.class).testTopic().subscribe().atMostOnceSource();

            List<String> messages = source.runWith(Sink.seq(), server.materializer()).toCompletableFuture().get();

            assertEquals(Arrays.asList("message1", "message2"), messages);
        });
    }

    public static class TestTopicServiceModule extends AbstractModule implements ServiceGuiceSupport {
        @Override
        protected void configure() {
            bindServices(serviceBinding(TestTopicService.class, TestTopicService.Impl.class));
        }
    }
}
