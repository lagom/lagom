/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.hello.impl;

import akka.Done;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;

import java.time.LocalDateTime;
import java.util.Optional;

import com.example.hello.impl.HelloCommand.Hello;
import com.example.hello.impl.HelloCommand.UseGreetingMessage;
import com.example.hello.impl.HelloEvent.GreetingMessageChanged;

public class HelloEntity extends PersistentEntity<HelloCommand, HelloEvent, HelloState> {
    @Override
    public Behavior initialBehavior(Optional<HelloState> snapshotState) {
 
        BehaviorBuilder b = newBehaviorBuilder(
                snapshotState.orElse(new HelloState("Hello", LocalDateTime.now().toString()))
        );

        b.setCommandHandler(UseGreetingMessage.class, (cmd, ctx) ->
                ctx.thenPersist(new GreetingMessageChanged(entityId(), cmd.message),
                        evt -> ctx.reply(Done.getInstance())
                )
        );

        b.setEventHandler(GreetingMessageChanged.class,
                evt -> new HelloState(evt.message, LocalDateTime.now().toString())
        );

        b.setReadOnlyCommandHandler(Hello.class,
                (cmd, ctx) -> ctx.reply(state().message + ", " + cmd.name + "!")
        );

        return b.build();
    }
}
