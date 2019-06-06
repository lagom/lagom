/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.serialization.Jsonable;

public class BlogEventTag {

  // #aggregate-tag
  public interface BlogEvent extends AggregateEvent<BlogEvent>, Jsonable {

    AggregateEventTag<BlogEvent> BLOG_EVENT_TAG = AggregateEventTag.of(BlogEvent.class);

    @Override
    default AggregateEventTag<BlogEvent> aggregateTag() {
      return BLOG_EVENT_TAG;
    }
  }
  // #aggregate-tag

}
