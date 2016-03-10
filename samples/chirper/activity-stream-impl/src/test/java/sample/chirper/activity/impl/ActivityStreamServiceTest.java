/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package sample.chirper.activity.impl;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import com.lightbend.lagom.javadsl.testkit.ServiceTest.Setup;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Test;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;
import sample.chirper.activity.api.ActivityStreamService;
import sample.chirper.chirp.api.*;
import sample.chirper.friend.api.*;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestSubscriber.Probe;
import akka.stream.testkit.javadsl.TestSink;

public class ActivityStreamServiceTest {

  private final Setup setup = defaultSetup().withCluster(false)
      .withConfigureBuilder(b -> b.overrides(bind(FriendService.class).to(FriendServiceStub.class),
          bind(ChirpService.class).to(ChirpServiceStub.class)));

  @Test
  public void shouldGetLiveFeed() throws Exception {
    withServer(setup, server -> {
      ActivityStreamService feedService = server.client(ActivityStreamService.class);
      Source<Chirp, ?> chirps = feedService.getLiveActivityStream().invoke("usr1", NotUsed.getInstance())
          .toCompletableFuture().get(3, SECONDS);
      Probe<Chirp> probe = chirps.runWith(TestSink.probe(server.system()), server.materializer());
      probe.request(10);
      assertEquals("msg1", probe.expectNext().message);
      assertEquals("msg2", probe.expectNext().message);
      probe.cancel();
    });
  }

  @Test
  public void shouldGetHistoricalFeed() throws Exception {
    withServer(setup, server -> {
      ActivityStreamService feedService = server.client(ActivityStreamService.class);
      Source<Chirp, ?> chirps = feedService.getHistoricalActivityStream().invoke("usr1", NotUsed.getInstance())
          .toCompletableFuture().get(3, SECONDS);
      Probe<Chirp> probe = chirps.runWith(TestSink.probe(server.system()), server.materializer());
      probe.request(10);
      assertEquals("msg1", probe.expectNext().message);
      probe.expectComplete();
    });
  }



  static class FriendServiceStub implements FriendService {

    private final User usr1 = new User("usr1", "User 1", 
        Optional.of(TreePVector.<String>empty().plus("usr2")));
    private final User usr2 = new User("usr2", "User 2");

    @Override
    public ServiceCall<String, NotUsed, User> getUser() {
      return (id, req) -> {
        if (id.equals(usr1.userId))
          return completedFuture(usr1);
        else if (id.equals(usr2.userId))
          return completedFuture(usr2);
        else
          throw new NotFound(id);
      };
    }

    @Override
    public ServiceCall<NotUsed, User, NotUsed> createUser() {
      return (id, req) -> completedFuture(NotUsed.getInstance());
    }

    @Override
    public ServiceCall<String, FriendId, NotUsed> addFriend() {
      return (id, req) -> completedFuture(NotUsed.getInstance());
    }

    @Override
    public ServiceCall<String, NotUsed, PSequence<String>> getFollowers() {
      return (id, req) -> {
        if (id.equals(usr1.userId))
          return completedFuture(TreePVector.<String>empty());
        else if (id.equals(usr2.userId))
          return completedFuture(TreePVector.<String>empty().plus("usr1"));
        else
          throw new NotFound(id);
      };
    }
  }

  static class ChirpServiceStub implements ChirpService {

    @Override
    public ServiceCall<String, Chirp, NotUsed> addChirp() {
      return (id, req) -> completedFuture(NotUsed.getInstance());
    }

    @Override
    public ServiceCall<NotUsed, LiveChirpsRequest, Source<Chirp, ?>> getLiveChirps() {
      return (id, req) -> {
        if (req.userIds.contains("usr2")) {
          Chirp c1 = new Chirp("usr2", "msg1");
          Chirp c2 = new Chirp("usr2", "msg2");
          return completedFuture(Source.from(Arrays.asList(c1, c2)));
        } else
          return completedFuture(Source.empty());
      };
    }

    @Override
    public ServiceCall<NotUsed, HistoricalChirpsRequest, Source<Chirp, ?>> getHistoricalChirps() {
      return (id, req) -> {
        if (req.userIds.contains("usr2")) {
          Chirp c1 = new Chirp("usr2", "msg1");
          return completedFuture(Source.single(c1));
        } else
          return completedFuture(Source.empty());
      };
    }

  }
}
