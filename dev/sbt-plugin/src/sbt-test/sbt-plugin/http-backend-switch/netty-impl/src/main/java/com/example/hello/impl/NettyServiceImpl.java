/*
 *
 */
package com.example.hello.impl;

import akka.NotUsed;
import com.example.hello.api.NettyService;
import com.lightbend.lagom.javadsl.api.ServiceCall;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of the AkkaHttpService.
 */
public class NettyServiceImpl implements NettyService {

    @Inject
    public NettyServiceImpl() {
    }

    @Override
    public ServiceCall<NotUsed, String> hello() {
        return request -> CompletableFuture.completedFuture(stackTrace());
    }

    private String stackTrace() {
        return Arrays.stream(new RuntimeException().getStackTrace())
                .map(ste -> ste.getClassName() + "\n")
                .filter(name -> name.toLowerCase().contains("server"))
                .reduce("", String::join);
    }

}
