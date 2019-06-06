/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.mb;

// #content
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = Void.class)
@JsonSubTypes({
  @JsonSubTypes.Type(BlogPostEvent.BlogPostCreated.class),
  @JsonSubTypes.Type(BlogPostEvent.BlogPostPublished.class)
})
public interface BlogPostEvent {

  String getPostId();

  @JsonTypeName("created")
  final class BlogPostCreated implements BlogPostEvent {
    private final String postId;
    private final String title;

    @JsonCreator
    public BlogPostCreated(String postId, String title) {
      this.postId = postId;
      this.title = title;
    }

    public String getPostId() {
      return postId;
    }

    public String getTitle() {
      return title;
    }
  }

  @JsonTypeName("published")
  final class BlogPostPublished implements BlogPostEvent {
    private final String postId;

    @JsonCreator
    public BlogPostPublished(String postId) {
      this.postId = postId;
    }

    public String getPostId() {
      return postId;
    }
  }
}
// #content
