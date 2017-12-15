/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import play.inject.Injector;

/**
 * Factory abstraction for creating child persistent entities.
 *
 * This can be used instead of directly invoking
 * {@link ChildPersistentEntity#create(Class, Injector, String, String, ActorContext)} as a method of indirection to
 * allow you to inject a mocked actor (eg an Akka TestKit probe) when testing.
 */
public interface ChildPersistentEntityFactory<Command> {

  /**
   * Create an entity.
   *
   * @param entityId  The id of the entity.
   * @param actorName The name of the actor.
   */
  ChildPersistentEntity<Command> create(String entityId, String actorName, ActorContext ctx);

  /**
   * Create a {@link ChildPersistentEntityFactory} for entities of the given type.
   *
   * @param entityClass The class of the entity.
   * @param injector The injector to create entities with.
   * @return The persistent entity factory.
   */
  static <Command> ChildPersistentEntityFactory<Command> forEntity(Class<? extends PersistentEntity<Command, ?, ?>> entityClass, Injector injector) {
    return (entityId, actorName, ctx) -> ChildPersistentEntity.create(entityClass, injector, entityId, actorName, ctx);
  }

  /**
   * Create a mocked {@link ChildPersistentEntityFactory}.
   *
   * All commands sent to any entities created will be passed as is to the <code>testProbe</code> actor. When
   * <code>stop</code> is invoked, the actor will be sent a <code>PoisonPill</code>.
   *
   * This is intended to be used with an Akka TestKit TestProbe, for example:
   *
   * <pre>
   * TestKit entityProbe = new TestKit(system, "persistent-entity");
   * ChildPersistentEntityFactory&lt;MyCommand&gt; childEntityFactory =
   *   ChildPersistentEntityFactory.mocked(MyEntity.class, entityProbe.getRef());
   *
   * // Create actor and cause it to send a command to the entity
   * ...
   *
   * entityProbe.expectMsg(new Duration(5, TimeUnit.SECONDS), new MyCommand());
   * entityProbe.reply(new SomeReply());
   * </pre>
   *
   * @param entityClass The entity class. This is used to infer the Command type.
   * @param testProbe The test probe ActorRef.
   */
  static <Command> ChildPersistentEntityFactory<Command> mocked(Class<? extends PersistentEntity<Command, ?, ?>> entityClass, ActorRef testProbe) {
    return (entityId, actorName, ctx) -> new ChildPersistentEntity<Command>(testProbe) {
      @Override
      public void stop() {
        testProbe.tell(PoisonPill.getInstance(), ActorRef.noSender());
      }
    };
  }
}
