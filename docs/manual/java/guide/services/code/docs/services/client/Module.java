/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services.client;

// #bind-hello-client
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.client.ServiceClientGuiceSupport;
import docs.services.HelloService;
import com.lightbend.lagom.javadsl.api.ServiceInfo;

public class Module extends AbstractModule implements ServiceClientGuiceSupport {

  protected void configure() {
    bindServiceInfo(ServiceInfo.of("hello-service"));
    bindClient(HelloService.class);
  }
}
// #bind-hello-client
