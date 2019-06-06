/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.advanced.akka

package workerservice {

  import com.lightbend.lagom.scaladsl.api.Service
  import com.lightbend.lagom.scaladsl.api.ServiceCall
  import docs.scaladsl.advanced.akka.dataobjects.Job
  import docs.scaladsl.advanced.akka.dataobjects.JobAccepted

  trait WorkerService extends Service {
    def doWork: ServiceCall[Job, JobAccepted]
    override def descriptor = {
      import Service._
      named("workservice").withCalls(call(doWork))
    }
  }
}

package dataobjects {

  //#dataobjects
  import play.api.libs.json.Format
  import play.api.libs.json.Json

  case class Job(jobId: String, task: String, payload: String)
  object Job {
    implicit val format: Format[Job] = Json.format
  }
  case class JobAccepted(jobId: String)
  object JobAccepted {
    implicit val format: Format[JobAccepted] = Json.format
  }
  //#dataobjects

}

package workerserviceimpl {

  import dataobjects.Job
  import dataobjects.JobAccepted
  import worker.Worker
  import workerservice.WorkerService

  //#service-impl
  import akka.actor.ActorSystem
  import akka.cluster.Cluster
  import akka.cluster.routing.ClusterRouterGroup
  import akka.cluster.routing.ClusterRouterGroupSettings
  import akka.routing.ConsistentHashingGroup
  import akka.pattern.ask
  import akka.util.Timeout
  import com.lightbend.lagom.scaladsl.api.ServiceCall
  import scala.concurrent.duration._

  class WorkerServiceImpl(system: ActorSystem) extends WorkerService {
    if (Cluster.get(system).selfRoles("worker-node")) {
      // start a worker actor on each node that has the "worker-node" role
      system.actorOf(Worker.props, "worker")
    }

    // start a consistent hashing group router,
    // which will delegate jobs to the workers. It is grouping
    // the jobs by their task, i.e. jobs with same task will be
    // delegated to same worker node
    val workerRouter = {
      val paths = List("/user/worker")
      val groupConf = ConsistentHashingGroup(paths, hashMapping = {
        case Job(_, task, _) => task
      })
      val routerProps = ClusterRouterGroup(
        groupConf,
        ClusterRouterGroupSettings(
          totalInstances = 1000,
          routeesPaths = paths,
          allowLocalRoutees = true,
          useRoles = Set("worker-node")
        )
      ).props
      system.actorOf(routerProps, "workerRouter")
    }

    def doWork = ServiceCall { job =>
      implicit val timeout = Timeout(5.seconds)
      (workerRouter ? job).mapTo[JobAccepted]
    }
  }
  //#service-impl
}

package worker {

  import dataobjects.Job
  import dataobjects.JobAccepted

  //#actor
  import akka.actor.Actor
  import akka.actor.Props
  import akka.event.Logging

  object Worker {
    def props = Props[Worker]
  }

  class Worker extends Actor {
    private val log = Logging.getLogger(context.system, this)

    override def receive = {
      case job @ Job(id, task, payload) =>
        log.info("Working on job: {}", job)
        sender ! JobAccepted(id)
        // perform the work...
        context.stop(self)
    }
  }
  //#actor
}
