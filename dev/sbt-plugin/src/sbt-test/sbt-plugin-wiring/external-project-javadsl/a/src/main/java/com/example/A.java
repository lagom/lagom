package com.example;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.*;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface A extends Service {
    ServiceCall<NotUsed, String> hello(String name);

    @Override
    default Descriptor descriptor() {
        return named("a").withCalls(
                pathCall("/hello/:name", this::hello)
        ).withAutoAcl(true);
    }
}