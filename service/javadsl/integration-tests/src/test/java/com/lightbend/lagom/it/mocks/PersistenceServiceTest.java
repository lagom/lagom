/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

import static org.junit.Assert.assertEquals;

import com.lightbend.lagom.javadsl.testkit.ServiceTest.TestServer;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class PersistenceServiceTest {
  
  private static TestServer server;
  private static PersistenceService client;
  
  @BeforeClass
  public static void setUp() {
    server = startServer(defaultSetup()
                    .withCassandra(true)
                    .configureBuilder(b -> b.bindings(new PersistenceServiceModule()))
    );
    client = server.client(PersistenceService.class);
  }
  
  @AfterClass
  public static void tearDown() {
    if (server != null) {
      server.stop();
      server = null;
      client = null;
    }
  }
  
  @Test
  public void shouldProvidePersistenceComponentsForInjection() throws Exception {
    assertEquals("ok", client.checkInjected().invoke().toCompletableFuture().get(10, SECONDS));
  }
  
  @Test
  public void shouldHaveAWorkingCassandraSession() throws Exception {
    assertEquals("ok", client.checkCassandraSession().invoke().toCompletableFuture().get(20, SECONDS));
  }
  
  

}
