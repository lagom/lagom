package docs.home.actor;

//#service-impl
import static akka.pattern.PatternsCS.ask;

import com.lightbend.lagom.javadsl.api.ServiceCall;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.routing.ClusterRouterGroup;
import akka.cluster.routing.ClusterRouterGroupSettings;
import akka.routing.ConsistentHashingGroup;
import akka.util.Timeout;

public class WorkerServiceImpl implements WorkerService {

  private final ActorRef workerRouter;

  @Inject
  public WorkerServiceImpl(ActorSystem system) {
    if (Cluster.get(system).getSelfRoles().contains("worker-node")) {
      // start a worker actor on each node that has the "worker-node" role
      system.actorOf(Worker.props(), "worker");
    }

    // start a consistent hashing group router,
    // which will delegate jobs to the workers. It is grouping
    // the jobs by their task, i.e. jobs with same task will be
    // delegated to same worker node
    List<String> paths = Arrays.asList("/user/worker");
    ConsistentHashingGroup groupConf = new ConsistentHashingGroup(paths)
      .withHashMapper(msg -> {
        if (msg instanceof Job) {
          return ((Job) msg).getTask();
        } else {
          return null;
        }
      });
      Props routerProps = new ClusterRouterGroup(groupConf,
        new ClusterRouterGroupSettings(1000, paths,
          true, "worker-node")).props();
    this.workerRouter = system.actorOf(routerProps, "workerRouter");
  }

  @Override
  public ServiceCall<Job, JobAccepted> doWork() {
    return job -> {
      // send the job to a worker, via the consistent hashing router
      CompletionStage<JobAccepted> reply = ask(workerRouter, job, Timeout.apply(
          5, TimeUnit.SECONDS))
        .thenApply(ack -> {
          return (JobAccepted) ack;
        });
      return reply;
    };
  }
}
//#service-impl
