/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package sample.helloworld.impl;

import java.time.LocalDateTime;
import java.util.Optional;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;

import akka.Done;
import sample.helloworld.impl.HelloCommand.Hello;
import sample.helloworld.impl.HelloCommand.UseGreetingMessage;
import sample.helloworld.impl.HelloEvent.GreetingMessageChanged;

/**
 * This is an event sourced entity. It has a state, {@link WorldState}, which
 * stores what the greeting should be (eg, "Hello").
 * <p>
 * Event sourced entities are interacted with by sending them commands. This
 * entity supports two commands, a {@link UseGreetingMessage} command, which is
 * used to change the greeting, and a {@link Hello} command, which is a read
 * only command which returns a greeting to the name specified by the command.
 * <p>
 * Commands get translated to events, and it's the events that get persisted by
 * the entity. Each event will have an event handler registered for it, and an
 * event handler simply applies an event to the current state. This will be done
 * when the event is first created, and it will also be done when the entity is
 * loaded from the database - each event will be replayed to recreate the state
 * of the entity.
 * <p>
 * This entity defines one event, the {@link GreetingMessageChanged} event,
 * which is emitted when a {@link UseGreetingMessage} command is received.
 */
public class HelloWorld extends PersistentEntity<HelloCommand, HelloEvent, WorldState> {

  /**
   * An entity can define different behaviours for different states, but it will
   * always start with an initial behaviour. This entity only has one behaviour.
   */
  @Override
  public Behavior initialBehavior(Optional<WorldState> snapshotState) {

    /*
     * Behaviour is defined using a behaviour builder. The behaviour builder
     * starts with a state, if this entity supports snapshotting (an
     * optimisation that allows the state itself to be persisted to combine many
     * events into one), then the passed in snapshotState may have a value that
     * can be used.
     *
     * Otherwise, the default state is to use the Hello greeting.
     */
    BehaviorBuilder b = newBehaviorBuilder(
        snapshotState.orElse(new WorldState("Hello", LocalDateTime.now().toString())));

    /*
     * Command handler for the UseGreetingMessage command.
     */
    b.setCommandHandler(UseGreetingMessage.class, (cmd, ctx) ->
    // In response to this command, we want to first persist it as a
    // GreetingMessageChanged event
    ctx.thenPersist(new GreetingMessageChanged(cmd.message),
        // Then once the event is successfully persisted, we respond with done.
        evt -> ctx.reply(Done.getInstance())));

    /*
     * Event handler for the GreetingMessageChanged event.
     */
    b.setEventHandler(GreetingMessageChanged.class,
        // We simply update the current state to use the greeting message from
        // the event.
        evt -> new WorldState(evt.message, LocalDateTime.now().toString()));

    /*
     * Command handler for the Hello command.
     */
    b.setReadOnlyCommandHandler(Hello.class,
        // Get the greeting from the current state, and prepend it to the name
        // that we're sending
        // a greeting to, and reply with that message.
        (cmd, ctx) -> ctx.reply(state().message + ", " + cmd.name + "!"));

    /*
     * We've defined all our behaviour, so build and return it.
     */
    return b.build();
  }

}
