/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.gettingstarted.helloservice;

import akka.actor.typed.ActorRef;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;

public interface HelloCommand {

  final class UseGreetingMessage implements HelloCommand {
    public final String message;
    public final ActorRef<Confirmation> replyTo;

    UseGreetingMessage(String message, ActorRef<Confirmation> replyTo) {
      this.message = message;
      this.replyTo = replyTo;
    }
  }

  final class Hello implements HelloCommand {
    public final String name;
    public final ActorRef<Greeting> replyTo;

    Hello(String name, ActorRef<Greeting> replyTo) {
      this.name = name;
      this.replyTo = replyTo;
    }
  }

  interface Confirmation {}

  final class Accepted implements Confirmation {}

  final class Rejected implements Confirmation {
    public final String reason;

    public Rejected(String reason) {
      this.reason = reason;
    }
  }

  final class Greeting {
    public final String message;

    public Greeting(String message) {
      this.message = message;
    }
  }
}
