/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

// #full-example
import com.fasterxml.jackson.annotation.JsonCreator;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;

import com.lightbend.lagom.serialization.Jsonable;
import org.pcollections.PSequence;

// #sharded-tags
interface BlogEvent extends Jsonable, AggregateEvent<BlogEvent> {

  int NUM_SHARDS = 20;

  AggregateEventShards<BlogEvent> TAG = AggregateEventTag.sharded(BlogEvent.class, NUM_SHARDS);

  @Override
  default AggregateEventShards<BlogEvent> aggregateTag() {
    return TAG;
  }
  // #sharded-tags

  final class PostAdded implements BlogEvent {
    private final String postId;
    private final PostContent content;

    @JsonCreator
    public PostAdded(String postId, PostContent content) {
      this.postId = postId;
      this.content = content;
    }

    public String getPostId() {
      return postId;
    }

    public PostContent getContent() {
      return content;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PostAdded postAdded = (PostAdded) o;

      if (!postId.equals(postAdded.postId)) return false;
      return content.equals(postAdded.content);
    }

    @Override
    public int hashCode() {
      int result = postId.hashCode();
      result = 31 * result + content.hashCode();
      return result;
    }
  }

  final class BodyChanged implements BlogEvent {
    private final String postId;
    private final String body;

    @JsonCreator
    public BodyChanged(String postId, String body) {
      this.postId = postId;
      this.body = body;
    }

    public String getPostId() {
      return postId;
    }

    public String getBody() {
      return body;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      BodyChanged that = (BodyChanged) o;

      if (!postId.equals(that.postId)) return false;
      return body.equals(that.body);
    }

    @Override
    public int hashCode() {
      int result = postId.hashCode();
      result = 31 * result + body.hashCode();
      return result;
    }
  }

  final class PostPublished implements BlogEvent {
    private final String postId;

    @JsonCreator
    public PostPublished(String postId) {
      this.postId = postId;
    }

    public String getPostId() {
      return postId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PostPublished that = (PostPublished) o;

      return postId.equals(that.postId);
    }

    @Override
    public int hashCode() {
      return postId.hashCode();
    }
  }
}
// #full-example
