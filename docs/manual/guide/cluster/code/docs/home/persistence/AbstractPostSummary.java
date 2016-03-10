package docs.home.persistence;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import com.lightbend.lagom.serialization.Jsonable;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = PostSummary.class)
public interface AbstractPostSummary extends Jsonable {
  @Value.Parameter
  String getPostId();

  @Value.Parameter
  public String getTitle();

}
