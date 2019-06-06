/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.lightbend.lagom.serialization.Jsonable;

// #full-example
public final class PostContent implements Jsonable {

  private final String title;
  private final String body;

  @JsonCreator
  public PostContent(String title, String body) {
    this.title = title;
    this.body = body;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PostContent that = (PostContent) o;

    if (!title.equals(that.title)) return false;
    return body.equals(that.body);
  }

  @Override
  public int hashCode() {
    int result = title.hashCode();
    result = 31 * result + body.hashCode();
    return result;
  }
}
// #full-example
