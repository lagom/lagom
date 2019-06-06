/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.actor;

import com.lightbend.lagom.javadsl.pubsub.TopicId;

import javax.inject.Inject;
import com.lightbend.lagom.javadsl.pubsub.PubSubRef;
import com.lightbend.lagom.javadsl.pubsub.PubSubRegistry;

import akka.actor.AbstractActor;

// #actor
public class Worker2 extends AbstractActor {

  private final PubSubRef<JobStatus> topic;

  @Inject
  public Worker2(PubSubRegistry pubSub) {
    topic = pubSub.refFor(TopicId.of(JobStatus.class, "jobs-status"));
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder().match(Job.class, this::perform).build();
  }

  private void perform(Job job) {
    sender().tell(JobAccepted.of(job.getJobId()), self());
    topic.publish(JobStatus.of(job.getJobId(), "started"));
    // perform the work...
    topic.publish(JobStatus.of(job.getJobId(), "done"));
    context().stop(self());
  }
}
// #actor
