package sample.chirper.chirp.impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

import sample.chirper.chirp.api.ChirpService;

public class ChirpModule extends AbstractModule implements ServiceGuiceSupport {
  @Override
  protected void configure() {
    bindServices(serviceBinding(ChirpService.class, ChirpServiceImpl.class));
  }
}
