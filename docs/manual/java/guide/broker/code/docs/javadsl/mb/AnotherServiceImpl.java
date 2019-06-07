/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.mb;

import akka.Done;
import akka.NotUsed;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.UniformFanInShape;
import akka.stream.UniformFanOutShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Merge;
import akka.stream.javadsl.Partition;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Message;
import com.lightbend.lagom.javadsl.broker.kafka.KafkaMetadataKeys;
import org.apache.kafka.common.header.Headers;
import java.util.Optional;

import javax.inject.Inject;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class AnotherServiceImpl implements AnotherService {

  // #inject-service
  private final HelloService helloService;

  @Inject
  public AnotherServiceImpl(HelloService helloService) {
    this.helloService = helloService;
  }
  // #inject-service

  public ServiceCall<NotUsed, NotUsed> audit() {
    // #subscribe-to-topic
    helloService
        .greetingsTopic()
        .subscribe() // <-- you get back a Subscriber instance
        .atLeastOnce(
            Flow.fromFunction(
                (GreetingMessage message) -> {
                  return doSomethingWithTheMessage(message);
                }));
    // #subscribe-to-topic
    return name -> completedFuture(NotUsed.getInstance());
  }

  private Done doSomethingWithTheMessage(GreetingMessage message) {
    throw new UnsupportedOperationException("Missing implementation");
  }

  private void subscribeWithMetadata() {
    // #subscribe-to-topic-with-metadata
    helloService
        .greetingsTopic()
        .subscribe()
        .withMetadata()
        .atLeastOnce(
            Flow.fromFunction(
                (Message<GreetingMessage> msg) -> {
                  GreetingMessage payload = msg.getPayload();
                  String messageKey = msg.messageKeyAsString();
                  Optional<Headers> kafkaHeaders = msg.get(KafkaMetadataKeys.HEADERS);
                  System.out.println(
                      "Message: " + payload + " Key: " + messageKey + " Headers: " + kafkaHeaders);
                  return Done.getInstance();
                }));
    // #subscribe-to-topic-with-metadata

  }

  private void skipMessages() {
    // #subscribe-to-topic-skip-messages
    helloService
        .greetingsTopic()
        .subscribe()
        .atLeastOnce(
            Flow.fromFunction(
                (GreetingMessage message) -> {
                  if (message.getMessage().equals("Kia ora")) {
                    return doSomethingWithTheMessage(message);
                  } else {
                    // Skip all messages where the message is not "Kia ora".
                    return Done.getInstance();
                  }
                }));
    // #subscribe-to-topic-skip-messages
  }
}
