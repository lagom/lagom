/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;

import akka.stream.javadsl.Source;

import com.lightbend.lagom.javadsl.api.*;
import com.lightbend.lagom.javadsl.api.transport.*;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface BlogService4 extends Service {

  ServiceCall<NotUsed, Source<BlogEvent.PostPublished, ?>> getNewPosts();

  @Override
  default Descriptor descriptor() {
    return named("/blogservice").withCalls(restCall(Method.GET, "/blogs", this::getNewPosts));
  }
}
