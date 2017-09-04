package ${package}.${service1Name}.api;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.pathCall;
import static com.lightbend.lagom.javadsl.api.Service.topic;

import akka.Done;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.broker.kafka.KafkaProperties;

/**
 * The ${service1ClassName} service interface.
 * <p>
 * This describes everything that Lagom needs to know about how to serve and
 * consume the ${service1ClassName}.
 */
public interface ${service1ClassName}Service extends Service {

  /**
   * Example: curl http://localhost:9000/api/${service1Name}/Alice
   */
  ServiceCall<NotUsed, String> hello(String id);


  /**
   * Example: curl -H "Content-Type: application/json" -X POST -d '{"message":
   * "Hi"}' http://localhost:9000/api/${service1Name}/Alice
   */
  ServiceCall<GreetingMessage, Done> useGreeting(String id);


  /**
   * This gets published to Kafka.
   */
  Topic<${service1ClassName}Event> helloEvents();

  @Override
  default Descriptor descriptor() {
    // @formatter:off
    return named("${service1Name}").withCalls(
        pathCall("/api/${service1Name}/:id",  this::hello),
        pathCall("/api/${service1Name}/:id", this::useGreeting)
      ).withTopics(
          topic("hello-events", this::helloEvents)
          // Kafka partitions messages, messages within the same partition will
          // be delivered in order, to ensure that all messages for the same user
          // go to the same partition (and hence are delivered in order with respect
          // to that user), we configure a partition key strategy that extracts the
          // name as the partition key.
          .withProperty(KafkaProperties.partitionKeyStrategy(), ${service1ClassName}Event::getName)
        ).withAutoAcl(true);
    // @formatter:on
  }
}
