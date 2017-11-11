/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.api.mock;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import com.lightbend.lagom.javadsl.api.Service;

import java.util.UUID;

import static com.lightbend.lagom.javadsl.api.Service.*;


public interface InvalidPathParameterService extends Service {
    
    ServiceCall<UUID, String> hello(Data invalidData);
    
    @Override
    default Descriptor descriptor() {
        return named("/invalid-service").withCalls(restCall(Method.GET, "/hello/:invalidData", this::hello));
    }
}

class Data {
}
