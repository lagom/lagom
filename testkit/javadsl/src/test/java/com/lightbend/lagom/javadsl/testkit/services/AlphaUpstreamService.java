/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit.services;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.broker.Topic;


public interface AlphaUpstreamService extends Service {

    String TOPIC_ID = "upstream-a-topic";

    Topic<AlphaEvent> messageTopic();

    @Override
    default Descriptor descriptor() {
        return Service.named("upstream-a")
                .publishing(
                        Service.topic(TOPIC_ID, this::messageTopic)
                );
    }

}
