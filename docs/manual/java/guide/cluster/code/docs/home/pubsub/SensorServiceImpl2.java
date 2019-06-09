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
import akka.stream.Materializer;
import akka.stream.javadsl.Source;

public class SensorServiceImpl2 implements SensorService2 {

  private final PubSubRegistry pubSub;
  private final Materializer materializer;

  @Inject
  public SensorServiceImpl2(PubSubRegistry pubSub, Materializer mat) {
    this.pubSub = pubSub;
    this.materializer = mat;
  }

  @Override
  public ServiceCall<Source<Temperature, ?>, NotUsed> registerTemperatures(String id) {
    return request -> {
      final PubSubRef<Temperature> topic = pubSub.refFor(TopicId.of(Temperature.class, id));
      request.runWith(topic.publisher(), materializer);
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
