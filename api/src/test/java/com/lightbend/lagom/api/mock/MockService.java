/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.api.mock;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import com.lightbend.lagom.javadsl.api.Service;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface MockService extends Service {

    ServiceCall<String, NotUsed, String> hello();

    @Override
    default Descriptor descriptor() {
        return named("/mock").with(restCall(Method.GET, "/hello/:name", hello()));
    }
}
