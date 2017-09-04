package ${package}.${service1Name}.impl;

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
import ${package}.${service1Name}.impl.${service1ClassName}Command.Hello;
import ${package}.${service1Name}.impl.${service1ClassName}Command.UseGreetingMessage;
import ${package}.${service1Name}.impl.${service1ClassName}Event.GreetingMessageChanged;

public class ${service1ClassName}EntityTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("${service1ClassName}EntityTest");
  }

  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void testHelloWorld() {
    PersistentEntityTestDriver<${service1ClassName}Command, ${service1ClassName}Event, ${service1ClassName}State> driver = new PersistentEntityTestDriver<>(system,
        new ${service1ClassName}Entity(), "world-1");

    Outcome<${service1ClassName}Event, ${service1ClassName}State> outcome1 = driver.run(new Hello("Alice"));
    assertEquals("Hello, Alice!", outcome1.getReplies().get(0));
    assertEquals(Collections.emptyList(), outcome1.issues());

    Outcome<${service1ClassName}Event, ${service1ClassName}State> outcome2 = driver.run(new UseGreetingMessage("Hi"),
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
