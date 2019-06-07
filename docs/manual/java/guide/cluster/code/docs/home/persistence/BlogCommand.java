/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

// #full-example
import com.fasterxml.jackson.annotation.JsonCreator;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.Jsonable;
import akka.Done;

public interface BlogCommand extends Jsonable {

  // #AddPost
  final class AddPost implements BlogCommand, PersistentEntity.ReplyType<AddPostDone> {
    private final PostContent content;

    @JsonCreator
    public AddPost(PostContent content) {
      this.content = content;
    }

    public PostContent getContent() {
      return content;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AddPost addPost = (AddPost) o;

      return content.equals(addPost.content);
    }

    @Override
    public int hashCode() {
      return content.hashCode();
    }
  }
  // #AddPost

  final class AddPostDone implements Jsonable {
    private final String postId;

    @JsonCreator
    public AddPostDone(String postId) {
      this.postId = postId;
    }

    public String getPostId() {
      return postId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AddPostDone that = (AddPostDone) o;

      return postId.equals(that.postId);
    }

    @Override
    public int hashCode() {
      return postId.hashCode();
    }
  }

  enum GetPost implements BlogCommand, PersistentEntity.ReplyType<PostContent> {
    INSTANCE
  }

  final class ChangeBody implements BlogCommand, PersistentEntity.ReplyType<Done> {
    private final String body;

    @JsonCreator
    public ChangeBody(String body) {
      this.body = body;
    }

    public String getBody() {
      return body;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ChangeBody that = (ChangeBody) o;

      return body.equals(that.body);
    }

    @Override
    public int hashCode() {
      return body.hashCode();
    }
  }

  enum Publish implements BlogCommand, PersistentEntity.ReplyType<Done> {
    INSTANCE
  }
}
// #full-example
