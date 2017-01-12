/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.two;

import static com.lightbend.lagom.javadsl.api.Service.call;
import static com.lightbend.lagom.javadsl.api.Service.named;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;

public interface ServiceA extends Service {

    ServiceCall<String, String> helloA();

    default Descriptor descriptor() {
        return named("/serviceA").withCalls(
            call(this::helloA)
        );
    }
}
