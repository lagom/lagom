/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package sample.chirper.chirp.impl;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.lightbend.lagom.javadsl.testkit.ServiceTest.TestServer;
import java.time.Instant;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pcollections.TreePVector;
import sample.chirper.chirp.api.Chirp;
import sample.chirper.chirp.api.ChirpService;
import sample.chirper.chirp.api.HistoricalChirpsRequest;
import sample.chirper.chirp.api.LiveChirpsRequest;

import akka.stream.javadsl.Source;
import akka.stream.testkit.TestSubscriber.Probe;
import akka.stream.testkit.javadsl.TestSink;

public class ChirpServiceTest {

  private static TestServer server;

  @BeforeClass
  public static void setUp() {
    server = startServer(defaultSetup());
  }

  @AfterClass
  public static void tearDown() {
    server.stop();
    server = null;
  }

  @Test
  public void shouldPublishShirpsToSubscribers() throws Exception {
    ChirpService chirpService = server.client(ChirpService.class);
    LiveChirpsRequest request = new LiveChirpsRequest(TreePVector.<String>empty().plus("usr1").plus("usr2"));
    Source<Chirp, ?> chirps1 = chirpService.getLiveChirps().invoke(request).toCompletableFuture().get(3, SECONDS);
    Probe<Chirp> probe1 = chirps1.runWith(TestSink.probe(server.system()), server.materializer());
    probe1.request(10);
    Source<Chirp, ?> chirps2 = chirpService.getLiveChirps().invoke(request).toCompletableFuture().get(3, SECONDS);
    Probe<Chirp> probe2 = chirps2.runWith(TestSink.probe(server.system()), server.materializer());
    probe2.request(10);

    Chirp chirp1 = new Chirp("usr1", "hello 1");
    chirpService.addChirp().invoke("usr1", chirp1).toCompletableFuture().get(3, SECONDS);
    probe1.expectNext(chirp1);
    probe2.expectNext(chirp1);

    Chirp chirp2 = new Chirp("usr1", "hello 2");
    chirpService.addChirp().invoke("usr1", chirp2).toCompletableFuture().get(3, SECONDS);
    probe1.expectNext(chirp2);
    probe2.expectNext(chirp2);

    Chirp chirp3 = new Chirp("usr2", "hello 3");
    chirpService.addChirp().invoke("usr2", chirp3).toCompletableFuture().get(3, SECONDS);
    probe1.expectNext(chirp3);
    probe2.expectNext(chirp3);

    probe1.cancel();
    probe2.cancel();
  }

  @Test
  public void shouldIncludeSomeOldChirpsInLiveFeed() throws Exception {
    ChirpService chirpService = server.client(ChirpService.class);

    Chirp chirp1 = new Chirp("usr3", "hi 1");
    chirpService.addChirp().invoke("usr3", chirp1).toCompletableFuture().get(3, SECONDS);

    Chirp chirp2 = new Chirp("usr4", "hi 2");
    chirpService.addChirp().invoke("usr4", chirp2).toCompletableFuture().get(3, SECONDS);

    LiveChirpsRequest request = new LiveChirpsRequest(TreePVector.<String>empty().plus("usr3").plus("usr4"));
    Source<Chirp, ?> chirps = chirpService.getLiveChirps().invoke(request).toCompletableFuture().get(3, SECONDS);
    Probe<Chirp> probe = chirps.runWith(TestSink.probe(server.system()), server.materializer());
    probe.request(10);
    probe.expectNextUnordered(chirp1, chirp2);

    Chirp chirp3 = new Chirp("usr4", "hi 3");
    chirpService.addChirp().invoke("usr4", chirp3).toCompletableFuture().get(3, SECONDS);
    probe.expectNext(chirp3);

    probe.cancel();
  }

  @Test
  public void shouldRetrieveOldChirps() throws Exception {
    ChirpService chirpService = server.client(ChirpService.class);

    Chirp chirp1 = new Chirp("usr5", "msg 1");
    chirpService.addChirp().invoke("usr5", chirp1).toCompletableFuture().get(3, SECONDS);

    Chirp chirp2 = new Chirp("usr6", "msg 2");
    chirpService.addChirp().invoke("usr6", chirp2).toCompletableFuture().get(3, SECONDS);

    HistoricalChirpsRequest request = new HistoricalChirpsRequest(Instant.now().minusSeconds(20),
        TreePVector.<String>empty().plus("usr5").plus("usr6"));
    Source<Chirp, ?> chirps = chirpService.getHistoricalChirps().invoke(request).toCompletableFuture().get(3, SECONDS);
    Probe<Chirp> probe = chirps.runWith(TestSink.probe(server.system()), server.materializer());
    probe.request(10);
    probe.expectNextUnordered(chirp1, chirp2);
    probe.expectComplete();
  }



}
