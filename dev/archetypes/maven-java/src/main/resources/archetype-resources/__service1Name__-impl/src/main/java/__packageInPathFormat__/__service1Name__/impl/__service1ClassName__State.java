package ${package}.${service1Name}.impl;

import lombok.Value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import com.lightbend.lagom.serialization.CompressedJsonable;

import java.time.LocalDateTime;

/**
 * The state for the {@link ${service1ClassName}} aggregate.
 */
@SuppressWarnings("serial")
@Value
@JsonDeserialize
public final class ${service1ClassName}State implements CompressedJsonable {
  public static final ${service1ClassName}State INITIAL = new ${service1ClassName}State("Hello", LocalDateTime.now().toString());
  public final String message;
  public final String timestamp;

  @JsonCreator
  public ${service1ClassName}State(String message, String timestamp) {
    this.message = Preconditions.checkNotNull(message, "message");
    this.timestamp = Preconditions.checkNotNull(timestamp, "timestamp");
  }

  public ${service1ClassName}State withMessage(String message) {
    return new ${service1ClassName}State(message, LocalDateTime.now().toString());
  }
}
