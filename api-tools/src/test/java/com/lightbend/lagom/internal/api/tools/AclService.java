/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api.tools;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface AclService extends Service {

    ServiceCall<NotUsed, NotUsed> getMock(String id);

    ServiceCall<NotUsed, NotUsed> addMock();

    default Descriptor descriptor() {
        return named("/aclservice").withCalls(
            restCall(Method.GET,  "/mocks/:id", this::getMock),
            restCall(Method.POST, "/mocks", this::addMock)
        ).withAutoAcl(true);
    }
}
