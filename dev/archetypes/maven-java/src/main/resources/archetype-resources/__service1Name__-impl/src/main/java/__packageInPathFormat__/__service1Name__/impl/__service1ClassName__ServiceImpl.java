package ${package}.${service1Name}.impl;

import akka.Done;
import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.japi.Pair;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.transport.BadRequest;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;

import ${package}.${service1Name}.api.GreetingMessage;
import ${package}.${service1Name}.api.${service1ClassName}Service;
import ${package}.${service1Name}.impl.${service1ClassName}Command.*;

import javax.inject.Inject;
import java.time.Duration;

/**
 * Implementation of the ${service1ClassName}Service.
 */
public class ${service1ClassName}ServiceImpl implements ${service1ClassName}Service {

  private final PersistentEntityRegistry persistentEntityRegistry;

  private final Duration askTimeout = Duration.ofSeconds(5);
  private ClusterSharding clusterSharding;

  @Inject
  public ${service1ClassName}ServiceImpl(PersistentEntityRegistry persistentEntityRegistry, ClusterSharding clusterSharding){
    this.clusterSharding=clusterSharding;
    // The persistent entity registry is only required to build an event stream for the TopicProducer
    this.persistentEntityRegistry=persistentEntityRegistry;

    // register the Aggregate as a sharded entity
    this.clusterSharding.init(
    Entity.of(
    ${service1ClassName}Aggregate.ENTITY_TYPE_KEY,
    ${service1ClassName}Aggregate::create
    )
    );
  }

  @Override
  public ServiceCall<NotUsed, String> hello(String id) {
    return request -> {

    // Look up the aggregete instance for the given ID.
    EntityRef<${service1ClassName}Command> ref = clusterSharding.entityRefFor(${service1ClassName}Aggregate.ENTITY_TYPE_KEY, id);
    // Ask the entity the Hello command.

    return ref.
      <${service1ClassName}Command.Greeting>ask(replyTo -> new Hello(id, replyTo), askTimeout)
      .thenApply(greeting -> greeting.message);    };
  }

  @Override
  public ServiceCall<GreetingMessage, Done> useGreeting(String id) {
    return request -> {

    // Look up the aggregete instance for the given ID.
    EntityRef<${service1ClassName}Command> ref = clusterSharding.entityRefFor(${service1ClassName}Aggregate.ENTITY_TYPE_KEY, id);
    // Tell the entity to use the greeting message specified.

    return ref.
      <${service1ClassName}Command.Confirmation>ask(replyTo -> new UseGreetingMessage(request.message, replyTo), askTimeout)
          .thenApply(confirmation -> {
              if (confirmation instanceof ${service1ClassName}Command.Accepted) {
                return Done.getInstance();
              } else {
                throw new BadRequest(((${service1ClassName}Command.Rejected) confirmation).reason);
              }
          });
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
