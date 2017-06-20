/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit;

import akka.japi.Pair;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.Offset;

import java.util.Arrays;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface TestTopicService extends Service {
    Topic<String> testTopic();

    @Override
    default Descriptor descriptor() {
        return named("testtopicservice")
                .withTopics(topic("testtopic", this::testTopic));
    }

    class Impl implements TestTopicService {
        @Override
        public Topic<String> testTopic() {
            return TopicProducer.singleStreamWithOffset(offset ->
                    Source.from(Arrays.asList(
                            Pair.create("message1", new Offset.Sequence(1)),
                            Pair.create("message2", new Offset.Sequence(2))
                    ))
            );
        }
    }
}
