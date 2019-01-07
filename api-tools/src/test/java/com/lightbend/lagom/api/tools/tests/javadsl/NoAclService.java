/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.api.tools.tests.javadsl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.restCall;

public interface NoAclService extends Service {

    ServiceCall<NotUsed, NotUsed> getMock(String id);

    default Descriptor descriptor() {
        return named("/noaclservice").withCalls(
            restCall(Method.GET,  "/mocks/:id", this::getMock)
        );
    }
}
