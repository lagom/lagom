/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.none;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.broker.Topic;

import static com.lightbend.lagom.javadsl.api.Service.named;

public interface PublisherService extends Service {

    Topic<String> messages();

    String TOPIC = "the-topic";

    default Descriptor descriptor() {
        return named("/publisher").withTopics(
                Service.topic(TOPIC, this::messages)
        );
    }
}
