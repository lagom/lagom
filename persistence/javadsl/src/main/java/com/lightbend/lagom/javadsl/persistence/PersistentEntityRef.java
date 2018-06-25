/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.NoSerializationVerificationNeeded;
import akka.pattern.PatternsCS;
import akka.util.Timeout;
import scala.concurrent.duration.FiniteDuration;

import java.io.NotSerializableException;
import java.io.ObjectStreamException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Commands are sent to a {@link PersistentEntity} using a
 * <code>PersistentEntityRef</code>. It is retrieved with
 * {@link PersistentEntityRegistry#refFor(Class, String)}.
 */
public final class PersistentEntityRef<Command> implements NoSerializationVerificationNeeded {
  private final String entityId;
  private final ActorRef region;
  private final Timeout timeout;

  public PersistentEntityRef(String entityId, ActorRef region, FiniteDuration askTimeout) {
    this.entityId = entityId;
    this.region = region;
    this.timeout = Timeout.apply(askTimeout);
  }

  /**
   * @deprecated Use {@link #PersistentEntityRef(String, ActorRef, FiniteDuration)} instead.
   */
  @Deprecated
  public PersistentEntityRef(String entityId, ActorRef region, ActorSystem system, FiniteDuration askTimeout) {
    this(entityId, region, askTimeout);
  }

  public String entityId() {
    return entityId;
  }

  /**
   * Send the <code>command</code> to the {@link PersistentEntity}. The returned
   * <code>CompletionStage</code> will be completed with the reply from the <code>PersistentEntity</code>.
   * The type of the reply is defined by the command (see {@link PersistentEntity.ReplyType}).
   * <p>
   * The <code>CompletionStage</code> may also be completed with failure, sent by the <code>PersistentEntity</code>
   * or a <code>akka.pattern.AskTimeoutException</code> if there is no reply within a timeout.
   * The timeout can defined in configuration or overridden using {@link #withAskTimeout(FiniteDuration)}.
   */
  @SuppressWarnings("unchecked")
  public <Reply, Cmd extends Object & PersistentEntity.ReplyType<Reply>> CompletionStage<Reply> ask(Cmd command) {
    CompletionStage<Object> future = PatternsCS.ask(region, new CommandEnvelope(entityId, command), timeout);

    return future.thenCompose(result -> {
      if (result instanceof Throwable) {
        CompletableFuture<Reply> failed = new CompletableFuture<>();
        failed.completeExceptionally((Throwable) result);
        return failed;
      } else {
        return CompletableFuture.completedFuture((Reply) result);
      }
    });

  }

  /**
   * The timeout for {@link #ask(Object)}. The timeout is by default defined in configuration
   * but it can be adjusted for a specific <code>PersistentEntityRef</code> using this method.
   * Note that this returns a new <code>PersistentEntityRef</code> instance with the given timeout
   * (<code>PersistentEntityRef</code> is immutable).
   */
  public PersistentEntityRef<Command> withAskTimeout(FiniteDuration timeout) {
    return new PersistentEntityRef<>(entityId, region, timeout);
  }

  //  Reasons for why we don't not support serialization of the PersistentEntityRef:
  //  - it will rarely be sent as a message itself, so providing a serializer will not help
  //  - it will be embedded in other messages and the only way we could support that
  //    transparently is to implement java serialization (readResolve, writeReplace)
  //    like ActorRef, but we don't want to encourage java serialization anyway
  //  - serializing/embedding the entityId String in other messages is simple
  //  - might be issues with the type `Command`?
  private Object writeReplace() throws ObjectStreamException {
    throw new NotSerializableException(getClass().getName() + " is not serializable. Send the entityId instead.");
  }

  @Override
  public String toString() {
    return "PersistentEntityRef(" + entityId + ")";
  }
}