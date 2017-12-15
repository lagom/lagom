package docs.home.persistence;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.sharding.ShardRegion;
import akka.testkit.javadsl.TestKit;
import akka.util.Timeout;
import com.lightbend.lagom.javadsl.persistence.ChildPersistentEntity;
import com.lightbend.lagom.javadsl.persistence.ChildPersistentEntityFactory;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import play.inject.Injector;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public interface ChildActors {

    //#create-child-persistent-entity-factory
    public class MyProcessManagerRouter {

        private final ActorRef processManager;

        @Inject
        public MyProcessManagerRouter(ActorSystem system, Injector injector) {
            ChildPersistentEntityFactory<MyCommand> factory =
                ChildPersistentEntityFactory.forEntity(
                    MyProcessManagerEntity.class, injector);

            processManager = ClusterSharding.get(system).start(
                "MyProcessManager",
                Props.create(() -> new MyProcessManager(factory)),
                ClusterShardingSettings.create(system),
                messageExtractor
            );
        }

        // ...
        //#create-child-persistent-entity-factory

        private ShardRegion.MessageExtractor messageExtractor;
    }

    public interface MyCommand {}

    public class StartProcess implements MyCommand, PersistentEntity.ReplyType<String> {

    }

    public class MyProcessManagerEntity extends PersistentEntity<MyCommand, String, String> {
        @Override
        public Behavior initialBehavior(Optional<String> snapshotState) {
            return newBehaviorBuilder("").build();
        }
    }

    //#create-child-persistent-entity
    public class MyProcessManager extends AbstractActor {

        private final ChildPersistentEntityFactory<MyCommand> factory;
        private ChildPersistentEntity<MyCommand> entity;

        public MyProcessManager(ChildPersistentEntityFactory<MyCommand> factory) {
            this.factory = factory;
        }

        @Override
        public void preStart() throws Exception {
            entity = factory.create(
                context().self().path().name(), // The entity id
                "entity",             // Name of the child actor
                context()                       // Actor context
            );
        }

        // ...
        //#create-child-persistent-entity

        @Override
        public Receive createReceive() {
            return receiveBuilder().build();
        }

        private void demonstrateTell() {
            //#child-persistent-entity-tell
            entity.tell(new StartProcess(), self());
            //#child-persistent-entity-tell
        }

        private void demonstrateForward() {
            //#child-persistent-entity-forward
            entity.forward(new StartProcess(), context());
            //#child-persistent-entity-forward
        }

        private void demonstrateAsk() {
            //#child-persistent-entity-ask
            CompletionStage<MappedReply> result =
                entity.ask(new StartProcess(),
                    Timeout.apply(3, TimeUnit.SECONDS)
                ).thenApply(MappedReply::new);

            akka.pattern.PatternsCS.pipe(result, context().dispatcher())
                .to(self());
            //#child-persistent-entity-ask
        }
    }

    public class MappedReply {
        public MappedReply(String reply) {}
    }

    // Because we don't want junit actually picking this up
    public @interface Test {}

    //#mock-child-persistent-entity
    public class MyProcessManagerTest {

        static ActorSystem system;

        @BeforeClass
        public void setup() {
            system = ActorSystem.create();
        }

        @AfterClass
        public void tearDown() {
            system.terminate();
        }

        @Test
        public void testStartProcess() {
            new TestKit(system) {{
                TestKit probe = new TestKit(system);

                ActorRef processManager = system.actorOf(
                    Props.create(() -> new MyProcessManager(
                        // Mock the child persistent entity factory to use
                        // our test probe.
                        ChildPersistentEntityFactory.mocked(
                            MyProcessManagerEntity.class,
                            probe.getRef()
                        )
                    ))
                );

                // Send the process manager a start message
                processManager.tell("start", getRef());
                // Expect the entity to receive a start process message
                probe.expectMsg(new StartProcess());
                // Simulate the entity to reply with a started message
                probe.reply("started");
                // Expect that message to be mapped forward back to us
                expectMsg(new MappedReply("started"));
            }};
        }
    }
    //#mock-child-persistent-entity

}
