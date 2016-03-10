package sample.chirper.friend.impl;

import com.google.inject.AbstractModule;
import sample.chirper.friend.api.FriendService;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class FriendModule extends AbstractModule implements ServiceGuiceSupport {
  @Override
  protected void configure() {
    bindServices(serviceBinding(FriendService.class, FriendServiceImpl.class));
  }
}
