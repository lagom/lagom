package com.example;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.*;
import java.util.concurrent.CompletableFuture;

public class AImpl implements A {
    @Override
    public ServiceCall<NotUsed, String> hello(String name) {
        return req -> CompletableFuture.completedFuture("Hello " + name);
    }
}