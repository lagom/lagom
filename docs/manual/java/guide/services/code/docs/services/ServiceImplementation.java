/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services;

import akka.japi.Pair;
import akka.stream.javadsl.Source;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.transport.Forbidden;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;
import com.lightbend.lagom.javadsl.server.HeaderServiceCall;
import com.lightbend.lagom.javadsl.server.ServerServiceCall;
import scala.concurrent.duration.FiniteDuration;

import java.security.Principal;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.time.Duration;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class ServiceImplementation {

  public static class HeaderServiceCallLambda implements HelloService {
    // #header-service-call-lambda
    public HeaderServiceCall<String, String> sayHello() {
      return (requestHeader, name) -> {
        String user = requestHeader.principal().map(Principal::getName).orElse("No one");
        String response = user + " wants to say hello to " + name;

        ResponseHeader responseHeader = ResponseHeader.OK.withHeader("Server", "Hello service");

        return completedFuture(Pair.create(responseHeader, response));
      };
    }
    // #header-service-call-lambda
  }

  public static class HeaderServiceCallOfLambda implements HelloService {
    // #header-service-call-of-lambda
    public ServerServiceCall<String, String> sayHello() {
      return HeaderServiceCall.of(
          (requestHeader, name) -> {
            String user = requestHeader.principal().map(Principal::getName).orElse("No one");
            String response = user + " wants to say hello to " + name;

            ResponseHeader responseHeader = ResponseHeader.OK.withHeader("Server", "Hello service");

            return completedFuture(Pair.create(responseHeader, response));
          });
    }
    // #header-service-call-of-lambda
  }

  public static class TickServiceCall implements FirstDescriptor.CallStream {
    // #tick-service-call
    public ServerServiceCall<String, Source<String, ?>> tick(int intervalMs) {
      return tickMessage -> {
        Duration interval = Duration.ofMillis(intervalMs);
        return completedFuture(Source.tick(interval, interval, tickMessage));
      };
    }
    // #tick-service-call
  }

  public static class HelloServiceCall implements FirstDescriptor.HelloStream {
    // #hello-service-call
    public ServerServiceCall<Source<String, ?>, Source<String, ?>> sayHello() {
      return names -> completedFuture(names.map(name -> "Hello " + name));
    }
    // #hello-service-call
  }

  public static class ServiceCallComposition {

    // #logging-service-call
    public <Request, Response> ServerServiceCall<Request, Response> logged(
        ServerServiceCall<Request, Response> serviceCall) {
      return HeaderServiceCall.compose(
          requestHeader -> {
            System.out.println("Received " + requestHeader.method() + " " + requestHeader.uri());
            return serviceCall;
          });
    }
    // #logging-service-call

    public class LoggedHelloService implements HelloService {
      // #logged-hello-service
      public ServerServiceCall<String, String> sayHello() {
        return logged(name -> completedFuture("Hello " + name));
      }
      // #logged-hello-service
    }

    public static class User {}

    // #user-storage
    interface UserStorage {
      CompletionStage<Optional<User>> lookupUser(String username);
    }
    // #user-storage

    private UserStorage userStorage;

    // #auth-service-call
    public <Request, Response> ServerServiceCall<Request, Response> authenticated(
        Function<User, ServerServiceCall<Request, Response>> serviceCall) {
      return HeaderServiceCall.composeAsync(
          requestHeader -> {

            // First lookup user
            CompletionStage<Optional<User>> userLookup =
                requestHeader
                    .principal()
                    .map(principal -> userStorage.lookupUser(principal.getName()))
                    .orElse(completedFuture(Optional.empty()));

            // Then, if it exists, apply it to the service call
            return userLookup.thenApply(
                maybeUser -> {
                  if (maybeUser.isPresent()) {
                    return serviceCall.apply(maybeUser.get());
                  } else {
                    throw new Forbidden("User must be authenticated to access this service call");
                  }
                });
          });
    }
    // #auth-service-call

    public class AuthHelloService implements HelloService {
      // #auth-hello-service
      public ServerServiceCall<String, String> sayHello() {
        return authenticated(user -> name -> completedFuture("Hello " + user));
      }
      // #auth-hello-service
    }

    // #compose-service-call
    public <Request, Response> ServerServiceCall<Request, Response> filter(
        Function<User, ServerServiceCall<Request, Response>> serviceCall) {
      return logged(authenticated(serviceCall));
    }
    // #compose-service-call

    public class FilterHelloService implements HelloService {
      // #filter-hello-service
      public ServerServiceCall<String, String> sayHello() {
        return filter(user -> name -> completedFuture("Hello " + user));
      }
      // #filter-hello-service
    }
  }
}
