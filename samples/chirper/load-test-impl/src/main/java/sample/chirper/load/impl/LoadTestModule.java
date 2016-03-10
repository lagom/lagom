package sample.chirper.load.impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import sample.chirper.activity.api.ActivityStreamService;
import sample.chirper.chirp.api.ChirpService;
import sample.chirper.friend.api.FriendService;
import sample.chirper.load.api.LoadTestService;

public class LoadTestModule extends AbstractModule implements ServiceGuiceSupport {
  @Override
  protected void configure() {
    bindServices(serviceBinding(LoadTestService.class, LoadTestServiceImpl.class));
    bindClient(FriendService.class);
    bindClient(ChirpService.class);
    bindClient(ActivityStreamService.class);
  }
}
