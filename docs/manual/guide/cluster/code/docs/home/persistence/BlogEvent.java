package docs.home.persistence;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import com.lightbend.lagom.serialization.Jsonable;
import org.immutables.value.Value;

//#full-example
interface BlogEvent extends Jsonable, AggregateEvent<BlogEvent> {

  @Override
  default public AggregateEventTag<BlogEvent> aggregateTag() {
    return BlogEventTag.INSTANCE;
  }

  @Value.Immutable
  @ImmutableStyle
  @JsonDeserialize(as = PostAdded.class)
  interface AbstractPostAdded extends BlogEvent {
    String getPostId();

    PostContent getContent();
  }

  @Value.Immutable
  @ImmutableStyle
  @JsonDeserialize(as = BodyChanged.class)
  interface AbstractBodyChanged extends BlogEvent {
    @Value.Parameter
    String getBody();
  }

  @Value.Immutable
  @ImmutableStyle
  @JsonDeserialize(as = PostPublished.class)
  interface AbstractPostPublished extends BlogEvent {
    @Value.Parameter
    String getPostId();
  }

}
//#full-example
