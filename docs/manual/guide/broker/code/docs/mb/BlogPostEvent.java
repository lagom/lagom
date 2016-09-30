package docs.mb;

//#content
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type",
        defaultImpl = Void.class)
@JsonSubTypes({
        @JsonSubTypes.Type(BlogPostEvent.BlogPostCreated.class),
        @JsonSubTypes.Type(BlogPostEvent.BlogPostPublished.class)
})
public interface BlogPostEvent {

    @JsonTypeName("created")
    final class BlogPostCreated {
        private final String postId;
        private final String title;

        @JsonCreator
        public BlogPostCreated(String postId, String title) {
            this.postId = postId;
            this.title = title;
        }

        // getters etc...
    }

    @JsonTypeName("published")
    final class BlogPostPublished {
        private final String postId;

        @JsonCreator
        public BlogPostPublished(String postId) {
            this.postId = postId;
        }

        // getters etc...
    }
}
//#content