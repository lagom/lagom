package docs.home.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import com.lightbend.lagom.serialization.Jsonable;
import java.util.Optional;
import org.immutables.value.Value;

//#full-example
@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = BlogState.class)
public abstract class AbstractBlogState implements Jsonable {

  public static final BlogState EMPTY = BlogState.of(Optional.empty());

  @Value.Parameter
  public abstract Optional<PostContent> getContent();

  @Value.Default
  @JsonProperty("isPublished")
  public boolean isPublished() {
    return false;
  }

  public BlogState withBody(String body) {
    if (isEmpty())
      throw new IllegalStateException("Can't set body without content");
    return BlogState.builder().from(this).content(
      Optional.of(getContent().get().withBody(body))).build();
  }

  @JsonIgnore
  public boolean isEmpty() {
    return getContent() == null;
  }

}
//#full-example
