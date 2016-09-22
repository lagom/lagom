package docs.mb;

import org.immutables.value.Value;

import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import com.lightbend.lagom.serialization.Jsonable;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * This interface defines all the events that the HelloWorld entity supports.
 * <p>
 * By convention, the events should be inner classes of the interface, which
 * makes it simple to get a complete picture of what events an entity has.
 */
public interface HelloEvent extends Jsonable, AggregateEvent<HelloEvent> {

  @Override
  default public AggregateEventTag<HelloEvent> aggregateTag() {
    return HelloEventTag.INSTANCE;
  }

  String getMessage();

  @Value.Immutable
  @ImmutableStyle
  @JsonDeserialize(as = GreetingMessageChanged.class)
  interface AbstractGreetingMessageChanged extends HelloEvent {
    @Value.Parameter
    String getMessage();
  }
}