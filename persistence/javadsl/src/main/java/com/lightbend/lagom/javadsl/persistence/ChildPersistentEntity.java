/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.lightbend.lagom.internal.javadsl.persistence.PersistentEntityActor;
import com.typesafe.config.Config;
import play.inject.Injector;
import scala.compat.java8.FutureConverters;
import scala.compat.java8.OptionConverters;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.runtime.AbstractFunction0;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * A persistent entity that runs as a child actor of the current actor context.
 */
public class ChildPersistentEntity<Command> {

  private final ActorRef actor;

  ChildPersistentEntity(ActorRef actor) {
    this.actor = actor;
  }

  /**
   * Get the actual actor for the entity.
   * <p>
   * This may be useful in order to watch the actors termination.
   */
  public ActorRef getActor() {
    return actor;
  }

  /**
   * Send a message to the actor.
   */
  public void tell(Command msg, ActorRef sender) {
    actor.tell(msg, sender);
  }

  /**
   * Ask the actor with a command.
   */
  @SuppressWarnings("unchecked")
  public <Reply, Cmd extends PersistentEntity.ReplyType<Reply>> CompletionStage<Reply> ask(Cmd msg, Timeout timeout) {
    return FutureConverters.toJava((Future<Reply>) Patterns.ask(actor, msg, timeout));
  }

  /**
   * Forward a message to the actor.
   */
  public void forward(Command msg, ActorContext ctx) {
    actor.forward(msg, ctx);
  }

  /**
   * Stop the persistent entity.
   */
  public void stop() {
    actor.tell(PersistentEntityActor.Stop$.MODULE$, ActorRef.noSender());
  }

  /**
   * Instantiate a persistent entity as a child of the current actor context.
   * <p>
   * Note that since this is a direct child, this provides no guarantee that this is the only entity with the given id
   * running in the cluster, or even in the actor system. It is up to the actor that instantiates this to ensure that
   * only one instance of the entity across the cluster is instantiated (by using cluster sharding to distribute
   * itself, for example), or to deal with inconsistencies that may arise due to having multiple entities instantiated
   * in the cluster.
   *
   * @param entityClass The class of the persistent entity.
   * @param injector    The injector to use to create the persistent entity.
   * @param entityId    The entity ID.
   * @param actorName   The actor name.
   * @param ctx         The actor context.
   */
  @SuppressWarnings("unchecked")
  public static <Command> ChildPersistentEntity<Command> create(Class<? extends PersistentEntity<Command, ?, ?>> entityClass,
      Injector injector, String entityId, String actorName, ActorContext ctx) {

    Optional<ActorRef> maybeEntity = OptionConverters.toJava(ctx.child(actorName));

    ActorRef actorRef = maybeEntity.orElseGet(() -> {
      Config conf = ctx.system().settings().config().getConfig("lagom.persistence");
      Optional<Object> snapshotAfter = Optional.of(conf.getString("snapshot-after"))
          .filter(str -> !str.equals("off"))
          .map(str -> conf.getInt("snapshot-after"));

      PersistentEntity<Command, ?, ?> entity = injector.instanceOf(entityClass);

      return ctx.actorOf(PersistentEntityActor.props(
          entity.entityTypeName(),
          Optional.of(entityId),
          new AbstractFunction0() {
            public Object apply() {
              return entity;
            }
          },
          snapshotAfter,
          Duration.Undefined()
      ), actorName);
    });

    return new ChildPersistentEntity(actorRef);
  }
}
