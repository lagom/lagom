package docs.home.pubsub;

//#service-impl
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
  public ServiceCall<String, Temperature, NotUsed> registerTemperature() {
    return (id, temperature) -> {
      final PubSubRef<Temperature> topic =
          pubSub.refFor(TopicId.of(Temperature.class, id));
      topic.publish(temperature);
      return CompletableFuture.completedFuture(NotUsed.getInstance());
    };
  }

  @Override
  public ServiceCall<String, NotUsed, Source<Temperature, ?>> temperatureStream() {
    return (id, request) -> {
      final PubSubRef<Temperature> topic =
          pubSub.refFor(TopicId.of(Temperature.class, id));
      return CompletableFuture.completedFuture(topic.subscriber());
    };
  }
}
//#service-impl
