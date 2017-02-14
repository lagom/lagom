/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static java.util.concurrent.TimeUnit.SECONDS;
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;


import com.lightbend.lagom.javadsl.testkit.ServiceTest.TestServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.Done;
import akka.NotUsed;

public class MockServiceTest {
  
  private static TestServer server;
  private static MockService client;

  @BeforeClass
  public static void setUp() {
    server = startServer(defaultSetup().withCluster(false)
        .withConfigureBuilder(b -> b.bindings(new MockServiceModule())));
    client = server.client(MockService.class);
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
  public void testInvoke() throws Exception {
    MockRequestEntity req = new MockRequestEntity("bar", 20);
    MockResponseEntity response = client.mockCall(10).invoke(req).toCompletableFuture().get(10, SECONDS);
    assertEquals(10, response.incomingId());
    assertEquals(req, response.incomingRequest());
  }
  
  @Test
  public void testInvokeForNotUsedParameters() throws Exception {
    MockServiceImpl.invoked.set(false);
    NotUsed reply = client.doNothing().invoke().toCompletableFuture().get(10, SECONDS);
    assertEquals(NotUsed.getInstance(), reply);
    assertTrue(MockServiceImpl.invoked.get());
  }
  
  @Test
  public void testInvokeForDoneParameters() throws Exception {
    MockServiceImpl.invoked.set(false);
    Done reply = client.doneCall().invoke(Done.getInstance())
        .toCompletableFuture().get(10, SECONDS);
    assertEquals(Done.getInstance(), reply);
  }
  
  // many more tests are written in MockServiceSpec and ErrorHandlingSpec
  

}
