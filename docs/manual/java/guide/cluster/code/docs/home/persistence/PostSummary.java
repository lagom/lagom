/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;

public final class PostSummary {

  private final String postId;
  private final String title;

  @JsonCreator
  public PostSummary(String postId, String title) {
    this.postId = postId;
    this.title = title;
  }

  public String getPostId() {
    return postId;
  }

  public String getTitle() {
    return title;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PostSummary that = (PostSummary) o;

    if (postId != null ? !postId.equals(that.postId) : that.postId != null) return false;
    return title != null ? title.equals(that.title) : that.title == null;
  }

  @Override
  public int hashCode() {
    int result = postId != null ? postId.hashCode() : 0;
    result = 31 * result + (title != null ? title.hashCode() : 0);
    return result;
  }
}
