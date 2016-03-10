package docs.home.persistence;

import com.lightbend.lagom.javadsl.api.*;
import com.lightbend.lagom.javadsl.api.transport.*;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface BlogService extends Service {

  ServiceCall<String, AddPost, String> addPost();

  @Override
  default Descriptor descriptor() {
    return named("/blogservice").with(
      restCall(Method.POST, "/blogs/:id", addPost())
    );
  }
}
