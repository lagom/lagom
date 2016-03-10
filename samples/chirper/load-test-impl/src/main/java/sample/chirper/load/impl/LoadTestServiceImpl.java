package sample.chirper.load.impl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import play.Logger;
import play.Logger.ALogger;
import sample.chirper.activity.api.ActivityStreamService;
import sample.chirper.chirp.api.Chirp;
import sample.chirper.chirp.api.ChirpService;
import sample.chirper.friend.api.FriendId;
import sample.chirper.friend.api.FriendService;
import sample.chirper.friend.api.User;
import sample.chirper.load.api.LoadTestService;
import sample.chirper.load.api.TestParams;
import scala.concurrent.duration.FiniteDuration;

import akka.japi.Pair;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

public class LoadTestServiceImpl implements LoadTestService {
  private final FriendService friendService;
  private final ActivityStreamService activityService;
  private final ChirpService chirpService;
  private final Materializer materializer;
  private final ALogger log = Logger.of(getClass());

  // to create "unique" user ids we prefix them with this, convenient
  // to not have overlapping user ids when running in dev mode
  private final AtomicLong runSeq = new AtomicLong((System.currentTimeMillis()
      - LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()) / 1000);

  @Inject
  public LoadTestServiceImpl(FriendService friendService, ActivityStreamService activityService,
      ChirpService chirpService, Materializer materializer) {
    this.friendService = friendService;
    this.activityService = activityService;
    this.chirpService = chirpService;
    this.materializer = materializer;
  }

  @Override
  public ServiceCall<NotUsed, NotUsed, Source<String, ?>> startLoad() {
    return (id, req) -> {
      return CompletableFuture.completedFuture(load(new TestParams()));
    };
  }

  @Override
  public ServiceCall<NotUsed, TestParams, NotUsed> startLoadHeadless() {
    return (id, params) -> {
      load(params).runWith(Sink.ignore(), materializer);
      return CompletableFuture.completedFuture(NotUsed.getInstance());
    };
  }

  private Source<String, ?> load(TestParams params) {
    long runSeqNr = runSeq.incrementAndGet();
    final String userIdPrefix = params.userIdPrefix.orElse("user-" + runSeqNr + "-");

    log.info("Starting load with parameters: " + params + ", users prefixed with: " + userIdPrefix);
    Source<Integer, ?> userNumbers = Source.range(1, params.users);
    Source<User, ?> users = userNumbers
        .map(n -> new User(userIdPrefix + n, userIdPrefix.toUpperCase() + n));
    Source<String, ?> createdUsers = users
        .mapAsync(params.parallelism, user -> friendService.createUser().invoke(user))
        .via(summary("created users"));

    Source<Pair<Integer, Integer>, ?> friendPairs = userNumbers.mapConcat(n -> {
      List<Pair<Integer, Integer>> pairs = new ArrayList<>();
      for (int i = 1; i <= params.friends; i++) {
        pairs.add(new Pair<>(n, n + i));
      }
      return pairs;
    });

    final AtomicLong chirpCount = new AtomicLong();
    Source<String, ?> addedFriends = friendPairs.mapAsyncUnordered(params.parallelism, pair -> {
      CompletionStage<NotUsed> invoked = friendService.addFriend().invoke(
          userIdPrefix + pair.first(), new FriendId(userIdPrefix + pair.second()));
      // start clients when last friend association has been created
      if (params.users == pair.first() && (params.users + params.friends) == pair.second())
        invoked.thenAccept(a -> startClients(params.clients, userIdPrefix, chirpCount, runSeqNr));
      return invoked;
    }).via(summary("added friends"));

    Source<Integer, ?> chirpNumbers = Source.range(1, params.chirps);
    Source<Chirp, ?> chirps = chirpNumbers.map(n -> {
      String userId = userIdPrefix + (n % params.users);
      String message = "Hello " + n + " from " + userId;
      return new Chirp(userId, message);
    });

    Source<String, ?> postedChirps = chirps.mapAsyncUnordered(params.parallelism, chirp -> {
      return chirpService.addChirp().invoke(chirp.userId, chirp);
    }).via(summary("posted chirp"));


    Source<String, ?> writes = Source.from(Arrays.asList(createdUsers, addedFriends, postedChirps))
        .flatMapConcat(s -> s);

    final FiniteDuration interval = FiniteDuration.create(5, TimeUnit.SECONDS);
    Source<String, ?> clientsThroughput = Source.tick(interval, interval, "tick")
        .scan(new Throughput(System.nanoTime(), System.nanoTime(), 0, 0), (t, tick) -> {
          long now = System.nanoTime();
          long totalCount = chirpCount.get();
          long count = totalCount - t.totalCount;
          return new Throughput(t.endTime, now, count, totalCount);
        })
        .filter(t -> t.throughput() > 0.0)
        .map(t -> "client throughput " + String.format("%.2f", t.throughput()) + " chirps/s from "
            + params.clients + " clients (total consumed: " + t.totalCount + " chirps)");

    Source<String, ?> output = Source.from(Arrays.asList(writes, clientsThroughput))
      .flatMapMerge(2, s -> s)
      .map(s -> {
        log.info(s);
        return s;
      }).map(s -> {
      if (runSeq.get() != runSeqNr) {
        String msg = "New test started, stopping previous";
        log.info(msg);
        throw new RuntimeException(msg);
      }
        return s;
      });

    return output;
  }


  private Flow<NotUsed, String, ?> summary(String title) {
    return Flow.of(NotUsed.class)
      .scan(0, (count, elem) -> count + 1)
      .drop(1)
      .groupedWithin(1000, FiniteDuration.create(1, TimeUnit.SECONDS))
      .map(list -> list.get(list.size() - 1))
      .map(c -> title + ": " + c);
  }

  private void startClients(int numberOfClients, String userIdPrefix, AtomicLong chirpCount, long runSeqNr) {
    log.info("starting " + numberOfClients + " clients for users prefixed with " + userIdPrefix);
    for (int n = 1; n <= numberOfClients; n++) {
      activityService.getLiveActivityStream().invoke(userIdPrefix + n, NotUsed.getInstance()).thenAccept(src -> {
        src
          .map( chirp -> {
            if (runSeq.get() != runSeqNr) {
              throw new RuntimeException("New test started, stopping previous clients");
            }
            return chirp;
          })
          .runForeach(chirp -> chirpCount.incrementAndGet(), materializer);
      });
    }
  }
}
