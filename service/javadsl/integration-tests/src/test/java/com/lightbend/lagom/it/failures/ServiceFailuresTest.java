/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.it.failures;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ServiceFailuresTest {

  @Test
  public void testInvokingANonPublicInterfaceThrowsAmicableMessage() throws Exception {
    Setup setup = defaultSetup()
        .withCluster(false)
        .configureBuilder(b -> b.bindings(new NotPublicInterfaceServiceModule()));
    try {
      startServer(setup);
      fail("Expected CreationException with cause IllegalArgumentException but nothing was thrown.");
    } catch (Exception t) {
      assertEquals("Service API must be described in a public interface", t.getCause().getMessage());
    }
  }

}

class NotPublicInterfaceServiceImpl implements NotPublicInterfaceService {

  @Override
  public ServiceCall<String, String> helloA() {
    return req -> CompletableFuture.completedFuture("???");
  }

}


class NotPublicInterfaceServiceModule extends AbstractModule implements ServiceGuiceSupport {
  @Override
  protected void configure() {
    bindServices(serviceBinding(NotPublicInterfaceService.class, NotPublicInterfaceServiceImpl.class));
  }
}
