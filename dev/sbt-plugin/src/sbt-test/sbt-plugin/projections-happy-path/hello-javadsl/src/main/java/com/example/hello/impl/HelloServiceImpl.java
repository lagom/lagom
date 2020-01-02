/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.hello.impl;

import akka.Done;
import akka.NotUsed;
import com.example.hello.api.HelloService;
import com.example.hello.impl.HelloCommand.Hello;
import com.example.hello.impl.HelloCommand.UseGreetingMessage;
import com.example.hello.impl.readsides.StartedProcessor;
import com.example.hello.impl.readsides.StoppedProcessor;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import com.lightbend.lagom.javadsl.projection.Projections;
import com.lightbend.lagom.projection.State;

import javax.inject.Inject;

public class HelloServiceImpl implements HelloService {
    private final PersistentEntityRegistry persistentEntityRegistry;
    private final Projections projections;
    private final StartedProcessor startedProcessor;
    private final StoppedProcessor stoppedProcessor;

    @Inject
    public HelloServiceImpl(
        PersistentEntityRegistry persistentEntityRegistry,
        Projections projections,
        ReadSide readSide,

        StartedProcessor startedProcessor,
        StoppedProcessor stoppedProcessor
    ) {
        this.projections = projections;
        this.startedProcessor = startedProcessor;
        this.stoppedProcessor = stoppedProcessor;

        // The following three lines are the key step on this scripted test:
        // request the workers of a projection to be started before registering the processor.
        // This service is setup to not start the projections eagerly (see application.conf) but we do
        // start one of the projections programmatically.
        projections.startAllWorkers(StartedProcessor.NAME);

        readSide.register(StartedProcessor.class);
        readSide.register(StoppedProcessor.class);

        this.persistentEntityRegistry = persistentEntityRegistry;
        persistentEntityRegistry.register(HelloEntity.class);

    }

    @Override
    public ServiceCall<NotUsed, String> hello(String id) {
        return notUsed -> {
            PersistentEntityRef<HelloCommand> ref = persistentEntityRegistry.refFor(HelloEntity.class, id);
            State state = null;
            try {
                state = projections.getStatus().toCompletableFuture().get();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            String st = state.toString();
            return ref.ask(
                new Hello(id)
            ).thenApply(msg -> {
                // `msg` is taken from the PE so it's consistent
                return msg + "\n" +
                    "Started reports: " + startedProcessor.getLastMessage(id) + "\n" +
                    "Stopped reports: " + stoppedProcessor.getLastMessage(id) + "\n"
                    ;
            });
        };
    }

    @Override
    public ServiceCall<NotUsed, Done> useGreeting(String id, String message) {
        return notUsed -> {
            PersistentEntityRef<HelloCommand> ref = persistentEntityRegistry.refFor(HelloEntity.class, id);
            return ref.ask(new UseGreetingMessage(message));
        };
    }

}
