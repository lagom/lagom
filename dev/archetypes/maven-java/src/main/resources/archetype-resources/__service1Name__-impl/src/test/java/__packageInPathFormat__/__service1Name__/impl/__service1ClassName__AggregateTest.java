package ${package}.${service1Name}.impl;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import ${package}.${service1Name}.impl.${service1ClassName}Command.Hello;
import ${package}.${service1Name}.impl.${service1ClassName}Command.UseGreetingMessage;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.UUID;

public class ${service1ClassName}AggregateTest {
  private static final String inmemConfig =
      "akka.persistence.journal.plugin = \"akka.persistence.journal.inmem\" \n";

  private static final String snapshotConfig =
      "akka.persistence.snapshot-store.plugin = \"akka.persistence.snapshot-store.local\" \n"
      + "akka.persistence.snapshot-store.local.dir = \"target/snapshot-"
      + UUID.randomUUID().toString()
      + "\" \n";

  private static final String config = inmemConfig + snapshotConfig;

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource(config);

  @Test
  public void testHello() {

      String id = "Alice";
      ActorRef<${service1ClassName}Command> ref =
        testKit.spawn(
          ${service1ClassName}Aggregate.create(
            new EntityContext(${service1ClassName}Aggregate.ENTITY_TYPE_KEY, id,  null)
          )
        );

      TestProbe<${service1ClassName}Command.Greeting> probe =
        testKit.createTestProbe(${service1ClassName}Command.Greeting.class);
      ref.tell(new Hello(id,probe.getRef()));
      probe.expectMessage(new ${service1ClassName}Command.Greeting("Hello, Alice!"));
  }

  @Test
  public void testUpdateGreeting() {
      String id = "Alice";
      ActorRef<${service1ClassName}Command> ref =
        testKit.spawn(
          ${service1ClassName}Aggregate.create(
           new EntityContext(${service1ClassName}Aggregate.ENTITY_TYPE_KEY, id,  null)
          )
          );

      TestProbe<${service1ClassName}Command.Confirmation> probe1 =
        testKit.createTestProbe(${service1ClassName}Command.Confirmation.class);
      ref.tell(new UseGreetingMessage("Hi", probe1.getRef()));
      probe1.expectMessage(new ${service1ClassName}Command.Accepted());

      TestProbe<${service1ClassName}Command.Greeting> probe2 =
        testKit.createTestProbe(${service1ClassName}Command.Greeting.class);
      ref.tell(new Hello(id,probe2.getRef()));
      probe2.expectMessage(new ${service1ClassName}Command.Greeting("Hi, Alice!"));
    }
}
