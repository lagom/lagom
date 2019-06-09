/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.actor

import com.lightbend.lagom.docs.ServiceSupport
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import akka.cluster.Cluster
import java.util.concurrent.TimeUnit

object ActorServiceSpec {
  def config = ConfigFactory.parseString("""
    akka.actor.provider = cluster
    akka.remote.artery.canonical.port = 0
    akka.remote.artery.canonical.hostname = 127.0.0.1
    """)
}

class ActorServiceSpec
    extends TestKit(ActorSystem("ActorServiceSpec", ActorServiceSpec.config))
    with ServiceSupport
    with BeforeAndAfterAll
    with TypeCheckedTripleEquals
    with ImplicitSender {

  val workerRoleConfig = ConfigFactory.parseString("akka.cluster.roles = [worker-node]")
  val node2            = ActorSystem("ActorServiceSpec", workerRoleConfig.withFallback(system.settings.config))
  val node3            = ActorSystem("ActorServiceSpec", workerRoleConfig.withFallback(system.settings.config))

  override def beforeAll {
    Cluster(system).join(Cluster(system).selfAddress)
    Cluster(node2).join(Cluster(system).selfAddress)
    Cluster(node3).join(Cluster(system).selfAddress)
    node2.actorOf(Worker.props(), "worker");
    node3.actorOf(Worker.props(), "worker");
    within(15.seconds) {
      awaitAssert {
        Cluster(system).state.members.size should ===(3)
      }
    }
  }

  override def afterAll {
    shutdown()
    shutdown(node2)
    shutdown(node3)
  }

  "Integration with actors" must {
    "work with for example clustered consistent hashing" in withServiceInstance[WorkerService](
      new WorkerServiceImpl(system)
    ).apply { app => client =>
      {
        val job = Job.of("123", "compute", "abc")

        // might take a while until cluster is formed and router knows about the nodes
        within(15.seconds) {
          awaitAssert {
            client.doWork().invoke(job).toCompletableFuture.get(3, TimeUnit.SECONDS) should ===(JobAccepted.of("123"))
          }
        }
      }

    }
  }

}
