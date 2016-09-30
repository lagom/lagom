package docs.mb;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
@JsonSerialize(as = GreetingMessage.class)
@JsonDeserialize(as = GreetingMessage.class)
public interface AbstractGreetingMessage {
    @Value.Parameter
    String message();
}
