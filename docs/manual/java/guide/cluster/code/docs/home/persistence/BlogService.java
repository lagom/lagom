/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

import com.lightbend.lagom.javadsl.api.*;
import com.lightbend.lagom.javadsl.api.transport.*;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface BlogService extends Service {

  ServiceCall<BlogCommand.AddPost, String> addPost(String id);

  @Override
  default Descriptor descriptor() {
    return named("/blogservice").withCalls(restCall(Method.POST, "/blogs/:id", this::addPost));
  }
}
