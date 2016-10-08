package docs.mb;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.broker.kafka.KafkaProperties;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface BlogPostService extends Service {
  @Override
  default Descriptor descriptor() {
    return named("blogpostservice")
            //#publishing
            .publishing(
                    topic("blogposts", this::blogPostEvents)
                        .withProperty(KafkaProperties.partitionKeyStrategy(),
                                BlogPostEvent::getPostId)
            );
            //#publishing
  }
  Topic<BlogPostEvent> blogPostEvents();
}
