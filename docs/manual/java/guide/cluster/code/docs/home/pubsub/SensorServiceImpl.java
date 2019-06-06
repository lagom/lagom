/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.pubsub;

// #service-impl
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.pubsub.PubSubRef;
import com.lightbend.lagom.javadsl.pubsub.PubSubRegistry;
import com.lightbend.lagom.javadsl.pubsub.TopicId;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import akka.stream.javadsl.Source;

public class SensorServiceImpl implements SensorService {

  private final PubSubRegistry pubSub;

  @Inject
  public SensorServiceImpl(PubSubRegistry pubSub) {
    this.pubSub = pubSub;
  }

  @Override
  public ServiceCall<Temperature, NotUsed> registerTemperature(String id) {
    return temperature -> {
      final PubSubRef<Temperature> topic = pubSub.refFor(TopicId.of(Temperature.class, id));
      topic.publish(temperature);
      return CompletableFuture.completedFuture(NotUsed.getInstance());
    };
  }

  @Override
  public ServiceCall<NotUsed, Source<Temperature, ?>> temperatureStream(String id) {
    return request -> {
      final PubSubRef<Temperature> topic = pubSub.refFor(TopicId.of(Temperature.class, id));
      return CompletableFuture.completedFuture(topic.subscriber());
    };
  }
}
// #service-impl
