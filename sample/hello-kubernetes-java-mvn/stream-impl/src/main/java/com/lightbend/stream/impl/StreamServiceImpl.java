package com.lightbend.stream.impl;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.hello.api.HelloService;
import com.lightbend.lagom.javadsl.api.ServiceLocator;
import com.lightbend.stream.api.StreamService;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Implementation of the HelloString.
 */
public class StreamServiceImpl implements StreamService {

    private final HelloService helloService;
    private final StreamRepository repository;
    private final ServiceLocator serviceLocator;

    @Inject
    public StreamServiceImpl(HelloService helloService, StreamRepository repository, ServiceLocator serviceLocator) {
        this.helloService = helloService;
        this.repository = repository;
        this.serviceLocator = serviceLocator;
    }

    @Override
    public ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> directStream() {
        return hellos -> completedFuture(
                hellos.mapAsync(8, name -> helloService.hello(name).invoke()));
    }

    @Override
    public ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> autonomousStream() {
        return hellos -> completedFuture(
                hellos.mapAsync(8, name -> repository.getMessage(name).thenApply(message ->
                        String.format("%s, %s!", message.orElse("Hello"), name)
                ))
        );
    }

    @Override
    public ServiceCall<NotUsed, String> helloProxy(String id) {
        return request -> helloService.hello(id).invoke();
    }

}
