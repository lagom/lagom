package com.lightbend.hello.impl;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.Optional;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.lightbend.lagom.javadsl.testkit.PersistentEntityTestDriver;
import com.lightbend.lagom.javadsl.testkit.PersistentEntityTestDriver.Outcome;

import akka.Done;
import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import com.lightbend.hello.impl.HelloCommand.Hello;
import com.lightbend.hello.impl.HelloCommand.UseGreetingMessage;
import com.lightbend.hello.impl.HelloEvent.GreetingMessageChanged;

public class HelloEntityTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("HelloEntityTest");
  }

  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void testHelloWorld() {
    PersistentEntityTestDriver<HelloCommand, HelloEvent, HelloState> driver = new PersistentEntityTestDriver<>(system,
        new HelloEntity(), "world-1");

    Outcome<HelloEvent, HelloState> outcome1 = driver.run(new Hello("Alice"));
    assertEquals("Hello, Alice!", outcome1.getReplies().get(0));
    assertEquals(Collections.emptyList(), outcome1.issues());

    Outcome<HelloEvent, HelloState> outcome2 = driver.run(new UseGreetingMessage("Hi"),
        new Hello("Bob"));
    assertEquals(1, outcome2.events().size());
    assertEquals(new GreetingMessageChanged("world-1", "Hi"), outcome2.events().get(0));
    assertEquals("Hi", outcome2.state().message);
    assertEquals(Done.getInstance(), outcome2.getReplies().get(0));
    assertEquals("Hi, Bob!", outcome2.getReplies().get(1));
    assertEquals(2, outcome2.getReplies().size());
    assertEquals(Collections.emptyList(), outcome2.issues());
  }

}
