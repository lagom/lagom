/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.gettingstarted.helloservice;

import akka.Done;
import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.japi.Pair;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.BadRequest;
import docs.javadsl.gettingstarted.helloservice.GreetingMessage;
import docs.javadsl.gettingstarted.helloservice.HelloCommand.*;

import javax.inject.Inject;
import java.time.Duration;

// #helloservice-impl
public class HelloServiceImpl implements HelloService {

  private final Duration askTimeout = Duration.ofSeconds(5);
  private ClusterSharding clusterSharding;

  @Inject
  public HelloServiceImpl(ClusterSharding clusterSharding) {
    this.clusterSharding = clusterSharding;
  }

  @Override
  public ServiceCall<NotUsed, String> hello(String id) {
    return request -> {
      EntityRef<HelloCommand> ref =
          clusterSharding.entityRefFor(HelloAggregate.ENTITY_TYPE_KEY, id);
      return ref.<HelloCommand.Greeting>ask(replyTo -> new Hello(id, replyTo), askTimeout)
          .thenApply(greeting -> greeting.message);
    };
  }

  @Override
  public ServiceCall<GreetingMessage, Done> useGreeting(String id) {
    return request -> {
      EntityRef<HelloCommand> ref =
          clusterSharding.entityRefFor(HelloAggregate.ENTITY_TYPE_KEY, id);
      return ref.<HelloCommand.Confirmation>ask(
              replyTo -> new UseGreetingMessage(request.message, replyTo), askTimeout)
          .thenApply(
              confirmation -> {
                if (confirmation instanceof HelloCommand.Accepted) {
                  return Done.getInstance();
                } else {
                  throw new BadRequest(((HelloCommand.Rejected) confirmation).reason);
                }
              });
    };
  }
}
// #helloservice-impl
