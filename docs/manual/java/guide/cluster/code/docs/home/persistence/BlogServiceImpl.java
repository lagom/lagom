/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

// #service-impl
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;

import javax.inject.Inject;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.api.*;

public class BlogServiceImpl implements BlogService {

  private final PersistentEntityRegistry persistentEntities;

  @Inject
  public BlogServiceImpl(PersistentEntityRegistry persistentEntities) {
    this.persistentEntities = persistentEntities;

    persistentEntities.register(Post.class);
  }

  @Override
  public ServiceCall<BlogCommand.AddPost, String> addPost(String id) {
    return request -> {
      PersistentEntityRef<BlogCommand> ref = persistentEntities.refFor(Post.class, id);
      return ref.ask(request).thenApply(ack -> "OK");
    };
  }
}
// #service-impl
