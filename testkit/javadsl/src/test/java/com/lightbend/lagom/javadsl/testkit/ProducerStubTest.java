/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit;


import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.testkit.services.*;
import org.junit.Assert;
import org.junit.Test;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;
import com.lightbend.lagom.javadsl.testkit.services.Module;
import scala.concurrent.duration.FiniteDuration;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;


public class ProducerStubTest {

    private static final ServiceTest.Setup setup = defaultSetup()
            .withCluster(false)
            .configureBuilder(builder ->
                    builder.bindings(
                            new Module()
                    ).overrides(
                            // build stubs eagerly so that only a single instance of the inner TopicStubs is built.
                            bind(AlphaUpstreamService.class).to(AlphaUpstreamServiceStub.class),
                            bind(BetaUpstreamService.class).to(BetaUpstreamServiceStub.class)
                    )
            );

    private static ProducerStub<AlphaEvent> producerAStub;
    private static ProducerStub<BetaEvent> producerBStub;

    @Test
    public void shouldAtLeastOnceSendToSubscribersWhatIsProduced() {
        withServer(setup, server -> {
            CharlieDownstreamService client = server.client(CharlieDownstreamService.class);

            int msg = 1;
            producerAStub.send(new AlphaEvent(msg));

            eventually(new FiniteDuration(5, SECONDS), new FiniteDuration(1, SECONDS), () -> {
                // check it was received downstream
                PSequence<ReceivedMessage> messages = client.retrieveMessagesC().invoke().toCompletableFuture().get(3, SECONDS);
                Assert.assertEquals(msg, messages.get(0).getMsg());
            });
        });
    }

    @Test
    public void shouldAtMostOnceSendToSubscribersWhatIsProduced() {
        withServer(setup, server -> {
            FoxtrotDownstreamService foxtrot = server.client(FoxtrotDownstreamService.class);

            List<Integer> msgs = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            msgs.forEach(msg -> producerAStub.send(new AlphaEvent(msg)));

            eventually(new FiniteDuration(5, SECONDS), new FiniteDuration(1, SECONDS), () -> {
                // check it was received downstream
                PSequence<ReceivedMessage> messages = foxtrot.retrieveMessagesF().invoke().toCompletableFuture().get(3, SECONDS);
                PSequence<ReceivedMessage> expected = TreePVector.from(
                        msgs.stream().map(msg -> new ReceivedMessage("A", msg)).collect(Collectors.toList()));
                assertEquals(expected, messages);
            });
        });
    }


    @Test
    public void shouldAtLeastOnceSendToSubscribersWhatIsProducedInTheRightOrder() {
        withServer(setup, server -> {
            CharlieDownstreamService client = server.client(CharlieDownstreamService.class);

            producerAStub.send(new AlphaEvent(1));
            producerAStub.send(new AlphaEvent(2));
            producerAStub.send(new AlphaEvent(3));

            eventually(new FiniteDuration(5, SECONDS), new FiniteDuration(1, SECONDS), () -> {
                // check it was received downstream
                PSequence<ReceivedMessage> messages = client.retrieveMessagesC().invoke().toCompletableFuture().get(3, SECONDS);
                List<ReceivedMessage> expected = Stream.of(1, 2, 3).map(i -> new ReceivedMessage("A", i)).collect(Collectors.toList());
                assertEquals(expected, messages);
            });
        });
    }

    @Test
    public void shouldAtLeastOnceSendToNewSubscribersWhatIsProducedSinceTheBeginningOfTimes() {
        withServer(setup, server -> {
            CharlieDownstreamService client = server.client(CharlieDownstreamService.class);

            // send before subscribing
            producerBStub.send(new BetaEvent(1));
            producerBStub.send(new BetaEvent(2));
            // subscribe
            client.startSubscriptionOnBeta().invoke().toCompletableFuture().get(3, SECONDS);

            // check it was received downstream
            eventually(new FiniteDuration(5, SECONDS), new FiniteDuration(1, SECONDS), () -> {
                PSequence<ReceivedMessage> messages = client.retrieveMessagesC().invoke().toCompletableFuture().get(3, SECONDS);
                List<ReceivedMessage> expected = Arrays.asList(new ReceivedMessage("B", 1), new ReceivedMessage("B", 2));
                assertEquals(expected, messages);
            });
        });
    }

    @Test
    public void shouldAtLeastOnceSendToMultipleSubscriberGroupsWhatIsProduced() {
        withServer(setup, server -> {
            CharlieDownstreamService charlie = server.client(CharlieDownstreamService.class);
            DeltaDownstreamService delta = server.client(DeltaDownstreamService.class);

            producerAStub.send(new AlphaEvent(1));
            producerAStub.send(new AlphaEvent(2));
            // send over B-topic before charlie is subscribed
            producerBStub.send(new BetaEvent(23));
            // subscribe charlie to b-topic
            charlie.startSubscriptionOnBeta().invoke().toCompletableFuture().get(3, SECONDS);

            eventually(new FiniteDuration(5, SECONDS), new FiniteDuration(1, SECONDS), () -> {
                PSequence<ReceivedMessage> messagesOnC = charlie.retrieveMessagesC().invoke().toCompletableFuture().get(3, SECONDS);
                PSequence<ReceivedMessage> messagesOnD = delta.retrieveMessagesD().invoke().toCompletableFuture().get(3, SECONDS);
                ReceivedMessage a1 = new ReceivedMessage("A", 1);
                ReceivedMessage a2 = new ReceivedMessage("A", 2);
                ReceivedMessage b23 = new ReceivedMessage("B", 23);

                assertEquals(Arrays.asList(a1, a2), messagesOnD);

                // messages incoming from Alpha and Beta could be received interlaced.
                List<ReceivedMessage> messagesOnCFromA = messagesOnC.stream().filter(m -> m.getSource().equals("A")).collect(Collectors.toList());
                List<ReceivedMessage> messagesOnCFromB = messagesOnC.stream().filter(m -> m.getSource().equals("B")).collect(Collectors.toList());
                assertEquals(Arrays.asList(a1, a2), messagesOnCFromA);
                assertEquals(b23, messagesOnCFromB.get(0));

            });
        });

    }


    @Test
    public void shouldNotReceiveMessagesFromTopicsNotSubscribedTo() {
        withServer(setup, server -> {
            CharlieDownstreamService charlie = server.client(CharlieDownstreamService.class);
            DeltaDownstreamService delta = server.client(DeltaDownstreamService.class);

            // send before subscribing
            producerAStub.send(new AlphaEvent(1));
            producerAStub.send(new AlphaEvent(2));
            producerBStub.send(new BetaEvent(23));

            eventually(new FiniteDuration(5, SECONDS), new FiniteDuration(1, SECONDS), () -> {
                charlie.startSubscriptionOnBeta().invoke().toCompletableFuture().get(3, SECONDS);
                PSequence<ReceivedMessage> messages = delta.retrieveMessagesD().invoke().toCompletableFuture().get(3, SECONDS);
                ReceivedMessage a1 = new ReceivedMessage("A", 1);
                ReceivedMessage a2 = new ReceivedMessage("A", 2);
                List<ReceivedMessage> expectedAs = Arrays.asList(a1, a2);
                assertEquals(expectedAs, messages);
            });
        });

    }


    static class AlphaUpstreamServiceStub implements AlphaUpstreamService {
        @Inject
        AlphaUpstreamServiceStub(ProducerStubFactory topicFactory) {
            producerAStub = topicFactory.producer(TOPIC_ID);
        }

        @Override
        public Topic<AlphaEvent> messageTopic() {
            return producerAStub.topic();
        }
    }

    static class BetaUpstreamServiceStub implements BetaUpstreamService {
        @Inject
        BetaUpstreamServiceStub(ProducerStubFactory topicFactory) {
            producerBStub = topicFactory.producer(TOPIC_ID);
        }

        @Override
        public Topic<BetaEvent> messageTopic() {
            return producerBStub.topic();
        }
    }
}
