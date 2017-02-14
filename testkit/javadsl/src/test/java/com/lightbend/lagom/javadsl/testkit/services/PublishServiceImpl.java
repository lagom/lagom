/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit.services;

import akka.japi.Pair;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.broker.TopicProducer;

import javax.inject.Inject;
import java.util.Arrays;

/**
 *
 */
public class PublishServiceImpl implements PublishService {

    @Inject
    public PublishServiceImpl(){

    }

    @Override
    public Topic<PublishEvent> messageTopic() {
        return TopicProducer.singleStreamWithOffset(offset ->
                Source
                        .from(Arrays.asList(new PublishEvent(23)))
                        .map(msg -> Pair.create(msg, offset))
        );
    }
}
