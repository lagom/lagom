/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.akka.impl;

import akka.NotUsed;
import com.example.hello.api.AkkaHttpService;
import com.lightbend.lagom.javadsl.api.ServiceCall;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of the AkkaHttpService.
 */
public class AkkaHttpServiceImpl implements AkkaHttpService {

    @Inject
    public AkkaHttpServiceImpl() {
    }

    @Override
    public ServiceCall<NotUsed, String> hello() {
        return request -> CompletableFuture.completedFuture( stackTrace() );
    }

    // When https://github.com/lagom/lagom/issues/1136 is fixed, we can remove this hack and assert
    // using HTTP headers
    private String stackTrace() {
        return Arrays.stream(new RuntimeException().getStackTrace())
                .map(ste -> ste.getClassName() + "\n")
                .filter(name -> name.toLowerCase().contains("server"))
                .reduce("", String::join);
    }

}
