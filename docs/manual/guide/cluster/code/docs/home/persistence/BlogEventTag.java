package docs.home.persistence;

import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;

//#tag
public class BlogEventTag {

  public static final AggregateEventTag<BlogEvent> INSTANCE =
    AggregateEventTag.of(BlogEvent.class);

}
//#tag
