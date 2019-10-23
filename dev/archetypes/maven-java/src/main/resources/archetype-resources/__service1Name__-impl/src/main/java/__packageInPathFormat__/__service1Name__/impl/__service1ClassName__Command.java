package ${package}.${service1Name}.impl;

import akka.actor.typed.ActorRef;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import com.lightbend.lagom.serialization.CompressedJsonable;
import com.lightbend.lagom.serialization.Jsonable;
import lombok.Value;

/**
  * This interface defines all the commands that the ${service1ClassName} aggregate supports.
  * <p>
  * By convention, the commands and replies should be inner classes of the
  * interface, which makes it simple to get a complete picture of what commands
  * an aggregate supports.
  */
public interface ${service1ClassName}Command extends Jsonable {

  /**
  * A command to switch the greeting message.
  * <p>
  * It has a reply type of {@link Confirmation}, which is sent back to the caller
  * when all the events emitted by this command are successfully persisted.
  */
  @SuppressWarnings("serial")
  @Value
  @JsonDeserialize
  final class UseGreetingMessage implements ${service1ClassName}Command, CompressedJsonable {
    public final String message;
    public final ActorRef<Confirmation> replyTo;
    
    @JsonCreator
    UseGreetingMessage(String message, ActorRef<Confirmation> replyTo) {
      this.message = Preconditions.checkNotNull(message, "message");
      this.replyTo = replyTo;
    }
  }

  /**
  * A command to say hello to someone using the current greeting message.
  * <p>
  * The reply type is {@link Greeting} and will contain the message to say to that
  * person.
  */
  @SuppressWarnings("serial")
  @Value
  @JsonDeserialize
  final class Hello implements ${service1ClassName}Command {
    public final String name;
    public final ActorRef<Greeting> replyTo;
    
    @JsonCreator
    Hello(String name, ActorRef<Greeting> replyTo) {
      this.name = Preconditions.checkNotNull(name, "name");
      this.replyTo = replyTo;
    }
  }

  // The commands above will use different reply types (see below all the reply types).
  
  /**
  * Super interface for Accepted/Rejected replies used by UseGreetingMessage
  */
  interface Confirmation {
  }

  @Value
  @JsonDeserialize
  final class Accepted implements Confirmation {
  }

  @Value
  @JsonDeserialize
  final class Rejected implements Confirmation {
    public final String reason;
    
    public Rejected(String reason) {
      this.reason = reason;
    }
  }

  /**
  * Reply type for a Hello command.
  */
  @Value
  @JsonDeserialize
  final class Greeting {
    public final String message;
    
    public Greeting(String message) {
      this.message = message;
    }
  }

}
