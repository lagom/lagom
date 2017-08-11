/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.two;

import static org.junit.Assert.assertEquals;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class ServiceABTest{
  
  @Test
  public void testInvoke() throws Exception {
    withServer(defaultSetup().withCluster(false).configureBuilder(b ->
      b.bindings(new ServiceAModule())
      .overrides(bind(ServiceB.class).to(ServiceBTestStub.class))), server -> {
    
      ServiceA client = server.client(ServiceA.class);
      String response = client.helloA().invoke("a").toCompletableFuture().get(10, TimeUnit.SECONDS); 
      assertEquals("hello: a", response);
    });
  }

}
