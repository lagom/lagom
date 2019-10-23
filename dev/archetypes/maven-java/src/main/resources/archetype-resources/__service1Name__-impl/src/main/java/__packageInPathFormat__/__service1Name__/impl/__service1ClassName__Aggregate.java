package ${package}.${service1Name}.impl;

import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.*;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import ${package}.${service1Name}.impl.${service1ClassName}Command.Hello;
import ${package}.${service1Name}.impl.${service1ClassName}Command.UseGreetingMessage;
import ${package}.${service1Name}.impl.${service1ClassName}Event.GreetingMessageChanged;

import java.util.Set;

/**
* This is an event sourced aggregate. It has a state, {@link ${service1ClassName}State}, which
* stores what the greeting should be (eg, "Hello").
* <p>
* Event sourced aggregate are interacted with by sending them commands. This
* aggregate supports two commands, a {@link UseGreetingMessage} command, which is
* used to change the greeting, and a {@link Hello} command, which is a read
* only command which returns a greeting to the name specified by the command.
* <p>
* Commands may emit events, and it's the events that get persisted.
* Each event will have an event handler registered for it, and an
* event handler simply applies an event to the current state. This will be done
* when the event is first created, and it will also be done when the entity is
* loaded from the database - each event will be replayed to recreate the state
* of the aggregate.
* <p>
* This aggregate defines one event, the {@link GreetingMessageChanged} event,
* which is emitted when a {@link UseGreetingMessage} command is received.
*/
public class ${service1ClassName}Aggregate extends EventSourcedBehaviorWithEnforcedReplies<${service1ClassName}Command, ${service1ClassName}Event, ${service1ClassName}State> {

  public static EntityTypeKey<${service1ClassName}Command> ENTITY_TYPE_KEY =
    EntityTypeKey
      .create(${service1ClassName}Command.class, "${service1ClassName}Aggregate");


  final private EntityContext<${service1ClassName}Command> entityContext;
  final private String entityId;

  ${service1ClassName}Aggregate(EntityContext<${service1ClassName}Command> entityContext) {
    super(
      PersistenceId.of(
          entityContext.getEntityTypeKey().name(),
          entityContext.getEntityId(),
          // The separator is an optional field. If you want to produce
          // events compatible with Lagom read-side processors, then the
          // separator must be an empty String.
          ""
        )
      );
    this.entityContext = entityContext;
    this.entityId = entityContext.getEntityId();
  }

  public static ${service1ClassName}Aggregate create(EntityContext<${service1ClassName}Command> entityContext) {
    return new ${service1ClassName}Aggregate(entityContext);
  }

  @Override
  public ${service1ClassName}State emptyState() {
    return ${service1ClassName}State.INITIAL;
  }


  @Override
  public CommandHandlerWithReply<${service1ClassName}Command, ${service1ClassName}Event, ${service1ClassName}State> commandHandler() {

    CommandHandlerWithReplyBuilder<${service1ClassName}Command, ${service1ClassName}Event, ${service1ClassName}State> builder = newCommandHandlerWithReplyBuilder();

    /*
    * Command handler for the UseGreetingMessage command.
    */
    builder.forAnyState()
      .onCommand(UseGreetingMessage.class, (state, cmd) ->
        Effect()
          // In response to this command, we want to first persist it as a
          // GreetingMessageChanged event
          .persist(new GreetingMessageChanged(entityId, cmd.message))
          // Then once the event is successfully persisted, we respond with done.
          .thenReply(cmd.replyTo, __ -> new ${service1ClassName}Command.Accepted())
      );

    /*
    * Command handler for the Hello command.
    */
    builder.forAnyState()
      .onCommand(Hello.class, (state, cmd) ->
        Effect().none()
          // Get the greeting from the current state, and prepend it to the name
          // that we're sending a greeting to, and reply with that message.
          .thenReply(cmd.replyTo, __ -> new ${service1ClassName}Command.Greeting(state.message + ", " + cmd.name + "!"))
      );

  return builder.build();

  }


  @Override
  public EventHandler<${service1ClassName}State, ${service1ClassName}Event> eventHandler() {
    EventHandlerBuilder<${service1ClassName}State, ${service1ClassName}Event> builder = newEventHandlerBuilder();

    /*
      * Event handler for the GreetingMessageChanged event.
      */
    builder.forAnyState()
      .onEvent(GreetingMessageChanged.class, (state, evt) ->
        // We simply update the current state to use the greeting message from
        // the event.
        state.withMessage(evt.message)
      );
    return builder.build();
  }


  @Override
  public Set<String> tagsFor(${service1ClassName}Event shoppingCartEvent) {
    return AkkaTaggerAdapter.fromLagom(entityContext, ${service1ClassName}Event.TAG).apply(shoppingCartEvent);
  }

}
