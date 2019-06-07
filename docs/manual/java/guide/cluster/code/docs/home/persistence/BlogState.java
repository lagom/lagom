/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lightbend.lagom.serialization.Jsonable;
import java.util.Optional;

// #full-example
public final class BlogState implements Jsonable {

  public static final BlogState EMPTY = new BlogState(Optional.empty(), false);

  private final Optional<PostContent> content;
  private final boolean published;

  @JsonCreator
  public BlogState(Optional<PostContent> content, boolean published) {
    this.content = content;
    this.published = published;
  }

  public BlogState withBody(String body) {
    if (isEmpty()) throw new IllegalStateException("Can't set body without content");
    PostContent c = content.get();
    return new BlogState(Optional.of(new PostContent(c.getTitle(), body)), published);
  }

  @JsonIgnore
  public boolean isEmpty() {
    return !content.isPresent();
  }

  public Optional<PostContent> getContent() {
    return content;
  }

  public boolean isPublished() {
    return published;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BlogState blogState = (BlogState) o;

    if (published != blogState.published) return false;
    return content.equals(blogState.content);
  }

  @Override
  public int hashCode() {
    int result = content.hashCode();
    result = 31 * result + (published ? 1 : 0);
    return result;
  }
}
// #full-example
