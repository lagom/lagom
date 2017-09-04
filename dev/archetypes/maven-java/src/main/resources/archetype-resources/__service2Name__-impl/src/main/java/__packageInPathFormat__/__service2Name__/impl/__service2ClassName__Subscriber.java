package ${package}.${service2Name}.impl;

import akka.Done;
import akka.stream.javadsl.Flow;
import ${package}.${service1Name}.api.${service1ClassName}Event;
import ${package}.${service1Name}.api.${service1ClassName}Service;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

/**
 * This subscribes to the ${service1ClassName}Service event stream.
 */
public class ${service2ClassName}Subscriber {

  @Inject
  public ${service2ClassName}Subscriber(${service1ClassName}Service ${service1Name}Service, ${service2ClassName}Repository repository) {
    // Create a subscriber
    ${service1Name}Service.helloEvents().subscribe()
      // And subscribe to it with at least once processing semantics.
      .atLeastOnce(
        // Create a flow that emits a Done for each message it processes
        Flow.<${service1ClassName}Event>create().mapAsync(1, event -> {

          if (event instanceof ${service1ClassName}Event.GreetingMessageChanged) {
            ${service1ClassName}Event.GreetingMessageChanged messageChanged = (${service1ClassName}Event.GreetingMessageChanged) event;
            // Update the message
            return repository.updateMessage(messageChanged.getName(), messageChanged.getMessage());

          } else {
            // Ignore all other events
            return CompletableFuture.completedFuture(Done.getInstance());
          }
        })
      );

  }
}
