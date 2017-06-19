/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit.services;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.broker.Topic;


public interface BetaUpstreamService extends Service {

    String TOPIC_ID = "upstream-b-topic";

    Topic<BetaEvent> messageTopic();

    @Override
    default Descriptor descriptor() {
        return Service.named("upstream-b")
                .withTopics(
                        Service.topic(TOPIC_ID, this::messageTopic)
                );
    }

}
