package ${package}.${service1Name}.impl;

import akka.Done;
import akka.NotUsed;
import akka.japi.Pair;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;

import java.util.Optional;

import javax.inject.Inject;
import ${package}.${service1Name}.api.GreetingMessage;
import ${package}.${service1Name}.api.${service1ClassName}Service;
import ${package}.${service1Name}.impl.${service1ClassName}Command.*;

/**
 * Implementation of the ${service1ClassName}Service.
 */
public class ${service1ClassName}ServiceImpl implements ${service1ClassName}Service {

  private final PersistentEntityRegistry persistentEntityRegistry;

  @Inject
  public ${service1ClassName}ServiceImpl(PersistentEntityRegistry persistentEntityRegistry) {
    this.persistentEntityRegistry = persistentEntityRegistry;
    persistentEntityRegistry.register(${service1ClassName}Entity.class);
  }

  @Override
  public ServiceCall<NotUsed, String> hello(String id) {
    return request -> {
      // Look up the hello world entity for the given ID.
      PersistentEntityRef<${service1ClassName}Command> ref = persistentEntityRegistry.refFor(${service1ClassName}Entity.class, id);
      // Ask the entity the Hello command.
      return ref.ask(new Hello(id));
    };
  }

  @Override
  public ServiceCall<GreetingMessage, Done> useGreeting(String id) {
    return request -> {
      // Look up the hello world entity for the given ID.
      PersistentEntityRef<${service1ClassName}Command> ref = persistentEntityRegistry.refFor(${service1ClassName}Entity.class, id);
      // Tell the entity to use the greeting message specified.
      return ref.ask(new UseGreetingMessage(request.message));
    };

  }

  @Override
  public Topic<${package}.${service1Name}.api.${service1ClassName}Event> helloEvents() {
    // We want to publish all the shards of the hello event
    return TopicProducer.taggedStreamWithOffset(${service1ClassName}Event.TAG.allTags(), (tag, offset) ->

      // Load the event stream for the passed in shard tag
      persistentEntityRegistry.eventStream(tag, offset).map(eventAndOffset -> {

      // Now we want to convert from the persisted event to the published event.
      // Although these two events are currently identical, in future they may
      // change and need to evolve separately, by separating them now we save
      // a lot of potential trouble in future.
      ${package}.${service1Name}.api.${service1ClassName}Event eventToPublish;

      if (eventAndOffset.first() instanceof ${service1ClassName}Event.GreetingMessageChanged) {
        ${service1ClassName}Event.GreetingMessageChanged messageChanged = (${service1ClassName}Event.GreetingMessageChanged) eventAndOffset.first();
        eventToPublish = new ${package}.${service1Name}.api.${service1ClassName}Event.GreetingMessageChanged(
          messageChanged.getName(), messageChanged.getMessage()
        );
      } else {
        throw new IllegalArgumentException("Unknown event: " + eventAndOffset.first());
      }

        // We return a pair of the translated event, and its offset, so that
        // Lagom can track which offsets have been published.
        return Pair.create(eventToPublish, eventAndOffset.second());
      })
    );
  }
}
