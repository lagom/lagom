package sample.chirper.chirp.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import com.datastax.driver.core.Row;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import com.lightbend.lagom.javadsl.pubsub.PubSubRef;
import com.lightbend.lagom.javadsl.pubsub.PubSubRegistry;
import com.lightbend.lagom.javadsl.pubsub.TopicId;

import akka.Done;
import akka.NotUsed;
import akka.stream.javadsl.Source;
import play.Logger;
import play.Logger.ALogger;
import sample.chirper.chirp.api.Chirp;
import sample.chirper.chirp.api.ChirpService;
import sample.chirper.chirp.api.HistoricalChirpsRequest;
import sample.chirper.chirp.api.LiveChirpsRequest;

public class ChirpServiceImpl implements ChirpService {

  private static final int MAX_TOPICS = 1024;
  private final PubSubRegistry topics;
  private final CassandraSession db;
  private final ALogger log = Logger.of(getClass());

  @Inject
  public ChirpServiceImpl(PubSubRegistry topics, CassandraSession db) {
    this.topics = topics;
    this.db = db;
    createTable();
  }

  private void createTable() {
    // @formatter:off
    CompletionStage<Done> result = db.executeCreateTable(
        "CREATE TABLE IF NOT EXISTS chirp ("
        + "userId text, timestamp bigint, uuid text, message text, "
        + "PRIMARY KEY (userId, timestamp, uuid))");
    // @formatter:on
    result.whenComplete((ok, err) -> {
      if (err != null) {
        log.error("Failed to create chirp table, due to: " + err.getMessage(), err);
      }
    });
  }

  @Override
  public ServiceCall<String, Chirp, NotUsed> addChirp() {
    return (userId, chirp) -> {
      if (!userId.equals(chirp.userId))
        throw new IllegalArgumentException("UserId " + userId + " did not match userId in " + chirp);
      PubSubRef<Chirp> topic = topics.refFor(TopicId.of(Chirp.class, topicQualifier(userId)));
      topic.publish(chirp);
      CompletionStage<NotUsed> result =
        db.executeWrite("INSERT INTO chirp (userId, uuid, timestamp, message) VALUES (?, ?, ?, ?)",
          chirp.userId, chirp.uuid, chirp.timestamp.toEpochMilli(),
          chirp.message).thenApply(done -> NotUsed.getInstance());
      return result;
    };
  }

  private String topicQualifier(String userId) {
    return String.valueOf(Math.abs(userId.hashCode()) % MAX_TOPICS);
  }

  @Override
  public ServiceCall<NotUsed, LiveChirpsRequest, Source<Chirp, ?>> getLiveChirps() {
    return (id, req) -> {
      return recentChirps(req.userIds).thenApply(recentChirps -> {
        List<Source<Chirp, ?>> sources = new ArrayList<>();
        for (String userId : req.userIds) {
          PubSubRef<Chirp> topic = topics.refFor(TopicId.of(Chirp.class, topicQualifier(userId)));
          sources.add(topic.subscriber());
        }
        HashSet<String> users = new HashSet<>(req.userIds);
        Source<Chirp, ?> publishedChirps = Source.from(sources).flatMapMerge(sources.size(), s -> s)
          .filter(c -> users.contains(c.userId));

        // We currently ignore the fact that it is possible to get duplicate chirps
        // from the recent and the topic. That can be solved with a de-duplication stage.
        return Source.from(recentChirps).concat(publishedChirps);
      });
    };
  }

  @Override
  public ServiceCall<NotUsed, HistoricalChirpsRequest, Source<Chirp, ?>> getHistoricalChirps() {
    return (id, req) -> {
      List<Source<Chirp, ?>> sources = new ArrayList<>();
      for (String userId : req.userIds) {
          Source<Chirp, NotUsed> select = db
            .select("SELECT * FROM chirp WHERE userId = ? AND timestamp >= ? ORDER BY timestamp ASC", userId,
                req.fromTime.toEpochMilli())
            .map(this::mapChirp);
        sources.add(select);
      }
        // Chirps from one user are ordered by timestamp, but chirps from different
        // users are not ordered. That can be improved by implementing a smarter
        // merge that takes the timestamps into account.
      Source<Chirp, ?> result = Source.from(sources).flatMapMerge(sources.size(), s -> s);
      return CompletableFuture.completedFuture(result);
    };
  }

  private Chirp mapChirp(Row row) {
    return new Chirp(row.getString("userId"), row.getString("message"), 
        Optional.of(Instant.ofEpochMilli(row.getLong("timestamp"))), Optional.of(row.getString("uuid")));
  }

  private CompletionStage<PSequence<Chirp>> recentChirps(PSequence<String> userIds) {
    int limit = 10;
    PSequence<CompletionStage<PSequence<Chirp>>> results = TreePVector.empty();
    for (String userId : userIds) {
      CompletionStage<PSequence<Chirp>> result = db
          .selectAll("SELECT * FROM chirp WHERE userId = ? ORDER BY timestamp DESC LIMIT ?", userId, limit)
          .thenApply(rows -> {
            List<Chirp> chirps = rows.stream().map(this::mapChirp).collect(Collectors.toList());
            return TreePVector.from(chirps);
          });
      results = results.plus(result);
    }

    CompletionStage<PSequence<Chirp>> combined = null;
    for (CompletionStage<PSequence<Chirp>> chirpsFromOneUser : results) {
      if (combined == null) {
        combined = chirpsFromOneUser;
      } else {
        combined = combined.thenCombine(chirpsFromOneUser, (a, b) -> a.plusAll(b));
      }
    }

    CompletionStage<PSequence<Chirp>> sortedLimited = combined.thenApply(all -> {
      List<Chirp> allSorted = new ArrayList<>(all);
      // reverse order
      Collections.sort(allSorted, (a, b) -> b.timestamp.compareTo(a.timestamp));
      List<Chirp> limited = allSorted.stream().limit(limit).collect(Collectors.toList());
      List<Chirp> reversed = new ArrayList<>(limited);
      Collections.reverse(reversed);
      return TreePVector.from(reversed);
    });

    return sortedLimited;
  }

}
