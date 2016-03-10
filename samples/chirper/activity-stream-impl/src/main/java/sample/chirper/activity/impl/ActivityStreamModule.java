package sample.chirper.activity.impl;

import sample.chirper.chirp.api.ChirpService;

import sample.chirper.friend.api.FriendService;
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import sample.chirper.activity.api.ActivityStreamService;

public class ActivityStreamModule extends AbstractModule implements ServiceGuiceSupport {

  @Override
  protected void configure() {
    bindServices(serviceBinding(ActivityStreamService.class, ActivityStreamServiceImpl.class));
    bindClient(FriendService.class);
    bindClient(ChirpService.class);
  }
}
