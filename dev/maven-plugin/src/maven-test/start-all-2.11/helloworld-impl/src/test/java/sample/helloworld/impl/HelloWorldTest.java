package sample.helloworld.impl;

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
import sample.helloworld.impl.HelloCommand.Hello;
import sample.helloworld.impl.HelloCommand.UseGreetingMessage;
import sample.helloworld.impl.HelloEvent.GreetingMessageChanged;

public class HelloWorldTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("HelloWorldTest");
  }

  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void testHelloWorld() {
    PersistentEntityTestDriver<HelloCommand, HelloEvent, WorldState> driver = new PersistentEntityTestDriver<>(system,
        new HelloWorld(), "world-1");

    Outcome<HelloEvent, WorldState> outcome1 = driver.run(new Hello("Alice", Optional.empty()));
    assertEquals("Hello, Alice!", outcome1.getReplies().get(0));
    assertEquals(Collections.emptyList(), outcome1.issues());

    Outcome<HelloEvent, WorldState> outcome2 = driver.run(new UseGreetingMessage("Hi"),
        new Hello("Bob", Optional.empty()));
    assertEquals(1, outcome2.events().size());
    assertEquals(new GreetingMessageChanged("Hi"), outcome2.events().get(0));
    assertEquals("Hi", outcome2.state().message);
    assertEquals(Done.getInstance(), outcome2.getReplies().get(0));
    assertEquals("Hi, Bob!", outcome2.getReplies().get(1));
    assertEquals(2, outcome2.getReplies().size());
    assertEquals(Collections.emptyList(), outcome2.issues());
  }

}
