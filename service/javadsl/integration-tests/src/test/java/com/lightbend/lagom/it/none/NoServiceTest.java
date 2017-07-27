/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.none;

import com.lightbend.lagom.javadsl.api.ServiceInfo;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.testkit.ProducerStub;
import com.lightbend.lagom.javadsl.testkit.ProducerStubFactory;
import org.junit.Test;

import javax.inject.Inject;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;
import static org.junit.Assert.assertEquals;

public class NoServiceTest {

    @Test
    public void testAModuleWithBrokerConsumptionButWithoutServiceCanBeRun() throws Exception {
        Setup setup = defaultSetup()
                .withCluster(false)
                .configureBuilder(b ->
                        b.bindings(new NoServiceModule()).overrides(
                                bind(PublisherService.class).to(PublisherServiceStub.class))
                );
        withServer(setup, server -> {
            ServiceInfo serviceInfo = server.injector().instanceOf(ServiceInfo.class);
            assertEquals("brokerConsumer", serviceInfo.serviceName());
        });
    }

    static class PublisherServiceStub implements PublisherService {

        private ProducerStub<String> producerStub;

        @Inject
        PublisherServiceStub(ProducerStubFactory stubFactory) {
            producerStub = stubFactory.producer(PublisherService.TOPIC);
        }

        @Override
        public Topic<String> messages() {
            return producerStub.topic();
        }
    }

}
