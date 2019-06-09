/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

// #service-impl
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.pubsub.PubSubRef;
import com.lightbend.lagom.javadsl.pubsub.PubSubRegistry;
import com.lightbend.lagom.javadsl.pubsub.TopicId;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;

import akka.stream.javadsl.Source;

public class BlogServiceImpl4 implements BlogService4 {

  private final PubSubRef<BlogEvent.PostPublished> publishedTopic;

  @Inject
  public BlogServiceImpl4(PubSubRegistry pubSub) {
    publishedTopic = pubSub.refFor(TopicId.of(BlogEvent.PostPublished.class, ""));
  }

  @Override
  public ServiceCall<NotUsed, Source<BlogEvent.PostPublished, ?>> getNewPosts() {
    return request -> CompletableFuture.completedFuture(publishedTopic.subscriber());
  }
}
// #service-impl
