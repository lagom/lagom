package api;

import com.lightbend.lagom.serialization.CompressedJsonable;
import java.util.Optional;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import org.immutables.value.Value;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import akka.Done;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;

public interface HelloCommand extends Jsonable {

  @Value.Immutable
  @ImmutableStyle
  @JsonDeserialize(as = UseGreetingMessage.class)
  public interface AbstractUseGreetingMessage extends HelloCommand, CompressedJsonable,
      PersistentEntity.ReplyType<Done> {
    @Value.Parameter
    String getMessage();
  }

  @Value.Immutable
  @ImmutableStyle
  @JsonDeserialize(as = Hello.class)
  public interface AbstractHello extends HelloCommand, PersistentEntity.ReplyType<String> {
    @Value.Parameter
    String getName();

    Optional<String> getOrganization();
  }

}
