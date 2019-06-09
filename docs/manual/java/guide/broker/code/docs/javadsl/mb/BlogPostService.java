/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.mb;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.broker.kafka.KafkaProperties;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface BlogPostService extends Service {
  @Override
  default Descriptor descriptor() {
    // #withTopics
    return named("blogpostservice")
        .withTopics(
            topic("blogposts", this::blogPostEvents)
                .withProperty(KafkaProperties.partitionKeyStrategy(), BlogPostEvent::getPostId));
    // #withTopics
  }

  Topic<BlogPostEvent> blogPostEvents();
}
