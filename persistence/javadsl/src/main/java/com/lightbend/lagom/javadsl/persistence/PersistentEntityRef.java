/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.NoSerializationVerificationNeeded;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.FiniteDuration;

import java.io.NotSerializableException;
import java.io.ObjectStreamException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Commands are sent to a {@link PersistentEntity} using a <code>PersistentEntityRef</code>. It is
 * retrieved with {@link PersistentEntityRegistry#refFor(Class, String)}.
 */
public final class PersistentEntityRef<Command> implements NoSerializationVerificationNeeded {
  private final String entityId;
  private final ActorRef region;
  private final Duration timeout;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  /**
   * @deprecated As of Lagom 1.5. Use {@link #PersistentEntityRef(String, ActorRef, Duration)}
   *     instead.
   */
  @Deprecated
  public PersistentEntityRef(String entityId, ActorRef region, FiniteDuration askTimeout) {
    this.entityId = entityId;
    this.region = region;
    this.timeout = Duration.ofMillis(askTimeout.toMillis());
  }

  public PersistentEntityRef(String entityId, ActorRef region, Duration askTimeout) {
    this.entityId = entityId;
    this.region = region;
    this.timeout = askTimeout;
  }

  /** @deprecated Use {@link #PersistentEntityRef(String, ActorRef, FiniteDuration)} instead. */
  @Deprecated
  public PersistentEntityRef(
      String entityId, ActorRef region, ActorSystem system, FiniteDuration askTimeout) {
    this(entityId, region, askTimeout);
  }

  public String entityId() {
    return entityId;
  }

  /**
   * Send the <code>command</code> to the {@link PersistentEntity}. The returned <code>
   * CompletionStage</code> will be completed with the reply from the <code>PersistentEntity</code>.
   * The type of the reply is defined by the command (see {@link PersistentEntity.ReplyType}).
   *
   * <p>The <code>CompletionStage</code> may also be completed with failure, sent by the <code>
   * PersistentEntity</code> or a <code>akka.pattern.AskTimeoutException</code> if there is no reply
   * within a timeout. The timeout can defined in configuration or overridden using {@link
   * #withAskTimeout(Duration)}.
   */
  @SuppressWarnings("unchecked")
  public <Reply, Cmd extends Object & PersistentEntity.ReplyType<Reply>> CompletionStage<Reply> ask(
      Cmd command) {

    CompletionStage<Object> future =
        Patterns.ask(region, new CommandEnvelope(entityId, command), timeout);

    return future
        .exceptionally(
            cause -> {
              if (cause instanceof AskTimeoutException) {
                String msg =
                    "Ask timed out on ["
                        + this
                        + "] after ["
                        + timeout.toMillis()
                        + " ms]. Message of type ["
                        + command.getClass()
                        + "]. "
                        + "A typical reason for `AskTimeoutException` is that the recipient actor didn't send a reply.";
                return new AskTimeoutException(msg, cause);

              } else {
                return cause;
              }
            })
        .thenCompose(
            result -> {
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
   * The timeout for {@link #ask(Object)}. The timeout is by default defined in configuration but it
   * can be adjusted for a specific <code>PersistentEntityRef</code> using this method. Note that
   * this returns a new <code>PersistentEntityRef</code> instance with the given timeout (<code>
   * PersistentEntityRef</code> is immutable).
   *
   * @deprecated As of Lagom 1.5. Use {@link #withAskTimeout(Duration)} instead.
   */
  @Deprecated
  public PersistentEntityRef<Command> withAskTimeout(FiniteDuration timeout) {
    return new PersistentEntityRef<>(entityId, region, timeout);
  }

  /**
   * The timeout for {@link #ask(Object)}. The timeout is by default defined in configuration but it
   * can be adjusted for a specific <code>PersistentEntityRef</code> using this method. Note that
   * this returns a new <code>PersistentEntityRef</code> instance with the given timeout (<code>
   * PersistentEntityRef</code> is immutable).
   */
  public PersistentEntityRef<Command> withAskTimeout(Duration timeout) {
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
    throw new NotSerializableException(
        getClass().getName() + " is not serializable. Send the entityId instead.");
  }

  @Override
  public String toString() {
    return "PersistentEntityRef(" + entityId + ")";
  }
}
