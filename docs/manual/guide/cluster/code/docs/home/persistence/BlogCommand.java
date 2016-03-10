package docs.home.persistence;

//#full-example
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.Jsonable;
import org.immutables.value.Value;
import akka.Done;

public interface BlogCommand extends Jsonable {

  //#AddPost
  @Value.Immutable
  @ImmutableStyle
  @JsonDeserialize(as = AddPost.class)
  interface AbstractAddPost
    extends BlogCommand, PersistentEntity.ReplyType<AddPostDone> {

    @Value.Parameter
    PostContent getContent();
  }
  //#AddPost

  @Value.Immutable
  @ImmutableStyle
  @JsonDeserialize(as = AddPostDone.class)
  interface AbstractAddPostDone extends Jsonable {
    @Value.Parameter
    String getPostId();
  }

  @Value.Immutable(singleton = true, builder = false)
  @ImmutableStyle
  @JsonDeserialize(as = GetPost.class)
  public abstract class AbstractGetPost implements BlogCommand, PersistentEntity.ReplyType<PostContent> {
    protected AbstractGetPost() {
    }
  }

  @Value.Immutable
  @ImmutableStyle
  @JsonDeserialize(as = ChangeBody.class)
  interface AbstractChangeBody extends BlogCommand, PersistentEntity.ReplyType<Done> {
    @Value.Parameter
    String getBody();
  }

  @Value.Immutable(singleton = true, builder = false)
  @ImmutableStyle
  @JsonDeserialize(as = Publish.class)
  public abstract class AbstractPublish implements BlogCommand, PersistentEntity.ReplyType<Done> {
    protected AbstractPublish() {
    }
  }

}
//#full-example
