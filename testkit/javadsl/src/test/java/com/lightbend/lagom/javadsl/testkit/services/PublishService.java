/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit.services;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.broker.Topic;

public interface PublishService extends Service {

    String TOPIC_ID = "pub-topic";

    Topic<PublishEvent> messageTopic();

    @Override
    default Descriptor descriptor() {
        return Service.named("publish-service")
                .withTopics(
                        Service.topic(TOPIC_ID, this::messageTopic)
                );
    }

}
