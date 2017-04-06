package impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.client.ServiceClientGuiceSupport;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import api.FooService;
import api.BarService;
import play.*;
import javax.inject.Inject;
import java.util.Date;
import java.io.*;

public class Module extends AbstractModule implements ServiceGuiceSupport, ServiceClientGuiceSupport {
	@Override
	protected void configure() {
		bindService(FooService.class, FooServiceImpl.class);
    bindClient(BarService.class);
		bind(OnStart.class).asEagerSingleton();
	}
}

class OnStart {

  @Inject
  public OnStart(Application app) {
  	doOnStart(app);
  }

  private void doOnStart(Application app) {
  	try {
  	  // open for append
      FileWriter writer = new FileWriter(app.getFile("target/reload.log"), true);
      writer.write(new Date() + " - reloaded\n");
      writer.close();

      if (app.configuration().getBoolean("fail", false)) {
        throw new RuntimeException();
      }
    }
    catch(IOException e) {
      throw new RuntimeException(e);
    }
  }
}
