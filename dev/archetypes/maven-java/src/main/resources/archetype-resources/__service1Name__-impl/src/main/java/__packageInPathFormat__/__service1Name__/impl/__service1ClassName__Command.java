package ${package}.${service1Name}.impl;

import java.util.Optional;

import lombok.Value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.CompressedJsonable;
import com.lightbend.lagom.serialization.Jsonable;

import akka.Done;

/**
 * This interface defines all the commands that the ${service1ClassName} entity supports.
 * 
 * By convention, the commands should be inner classes of the interface, which
 * makes it simple to get a complete picture of what commands an entity
 * supports.
 */
public interface ${service1ClassName}Command extends Jsonable {

  /**
   * A command to switch the greeting message.
   * <p>
   * It has a reply type of {@link akka.Done}, which is sent back to the caller
   * when all the events emitted by this command are successfully persisted.
   */
  @SuppressWarnings("serial")
  @Value
  @JsonDeserialize
  final class UseGreetingMessage implements ${service1ClassName}Command, CompressedJsonable, PersistentEntity.ReplyType<Done> {
    public final String message;

    @JsonCreator
    public UseGreetingMessage(String message) {
      this.message = Preconditions.checkNotNull(message, "message");
    }
  }

  /**
   * A command to say hello to someone using the current greeting message.
   * <p>
   * The reply type is String, and will contain the message to say to that
   * person.
   */
  @SuppressWarnings("serial")
  @Value
  @JsonDeserialize
  final class Hello implements ${service1ClassName}Command, PersistentEntity.ReplyType<String> {

    public final String name;

    @JsonCreator
    public Hello(String name) {
      this.name = Preconditions.checkNotNull(name, "name");
    }
  }

}
