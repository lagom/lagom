package docs.scaladsl.cluster.childactors

import akka.actor.{Actor, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.persistence.{ChildPersistentEntity, ChildPersistentEntityFactory, PersistentEntity}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

//#create-child-persistent-entity-factory
class MyProcessManagerRouter(system: ActorSystem, entityFactory: () => MyProcessManagerEntity) {
  private val processManager = {
    val factory = ChildPersistentEntityFactory.forEntity(entityFactory)
    ClusterSharding(system).start(
      "MyProcessManager",
      Props(new MyProcessManager(factory)),
      ClusterShardingSettings(system),
      extractEntityId,
      extractShardId
    )
  }
  //#create-child-persistent-entity-factory

  private def extractEntityId: ShardRegion.ExtractEntityId = PartialFunction.empty
  private def extractShardId: ShardRegion.ExtractShardId = _ => ""
}

trait MyCommand

case object StartProcess extends MyCommand with ReplyType[String]

class MyProcessManagerEntity extends PersistentEntity {
  override type Command = MyCommand
  override type Event = String
  override type State = String
  override def initialState = ""
  override def behavior = {
    case "" => Actions()
  }
}

//#create-child-persistent-entity
class MyProcessManager(
    factory: ChildPersistentEntityFactory[MyProcessManagerEntity]
  ) extends Actor {

  private var entity: ChildPersistentEntity[MyCommand] = _

  override def preStart(): Unit = {
    entity = factory(
      context.self.path.name, // The entity id
      "entity"                // Name of the child actor
    )
  }
  //#create-child-persistent-entity


  override def receive = PartialFunction.empty

  private def demonstrateTell(): Unit = {
    //#child-persistent-entity-tell
    entity ! StartProcess
    //#child-persistent-entity-tell
  }

  private def demonstrateForward(): Unit = {
    //#child-persistent-entity-forward
    entity forward StartProcess
    //#child-persistent-entity-forward
  }

  private def demonstrateAsk(): Unit = {
    //#child-persistent-entity-ask
    import scala.concurrent.duration._
    implicit val timeout = Timeout(3.seconds)

    import context.dispatcher
    import akka.pattern.pipe

    val result = (entity ? StartProcess)
      .map(MappedReply.apply)

    result pipeTo self
    //#child-persistent-entity-ask
  }
}

case class MappedReply(reply: String)

// Ensures scalatest can't find the contained test
private abstract class Ignore {
  //#mock-child-persistent-entity
  class MyProcessManagerTest extends TestKit(ActorSystem())
    with WordSpecLike with BeforeAndAfterAll {

    override protected def afterAll(): Unit = {
      system.terminate()
    }

    "My process manager" should {
      "allow starting a process" in {
        val probe = TestProbe()

        val processManager = system.actorOf(Props(new MyProcessManager(
          // Mock the child persistent entity factory to use
          // our test probe.
          ChildPersistentEntityFactory.mocked[MyProcessManagerEntity](probe.testActor)
        )))

        // Send the process manager a start message
        processManager ! "start"
        // Expect the entity to receive a start process message
        probe.expectMsg(StartProcess)
        // Simulate the entity to reply with a started message
        probe reply "started"
        // Expect that message to be mapped forward back to us
        expectMsg(MappedReply("started"))

      }
    }
  }
  //#mock-child-persistent-entity
}
