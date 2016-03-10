package docs.home.persistence;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import com.lightbend.lagom.serialization.Jsonable;
import org.immutables.value.Value;

//#full-example
@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = PostContent.class)
public interface AbstractPostContent extends Jsonable {
  @Value.Parameter
  public String getTitle();

  @Value.Parameter
  public String getBody();

}
//#full-example
