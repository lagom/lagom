/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.actor;

// #actor
import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class Worker extends AbstractActor {

  public static Props props() {
    return Props.create(Worker.class);
  }

  private final LoggingAdapter log = Logging.getLogger(context().system(), this);

  @Override
  public Receive createReceive() {
    return receiveBuilder().match(Job.class, this::perform).build();
  }

  private void perform(Job job) {
    log.info("Working on job: {}", job);
    sender().tell(JobAccepted.of(job.getJobId()), self());
    // perform the work...
    context().stop(self());
  }
}
// #actor
