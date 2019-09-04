/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.hello.api;

import akka.Done;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface HelloService extends Service {
    /**
     * Example: curl http://localhost:9000/api/hello/Alice
     */
    ServiceCall<NotUsed, String> hello(String id);

    /**
     * Example: curl http://localhost:9000/api/set/Alice/Hi
     */
    ServiceCall<NotUsed, Done> useGreeting(String id, String message);

    @Override
    default Descriptor descriptor() {
        return named("hello")
            .withCalls(
                pathCall("/api/hello/:id", this::hello),
                pathCall("/api/set/:id/:message", this::useGreeting)
            ).withAutoAcl(true);
    }
}
