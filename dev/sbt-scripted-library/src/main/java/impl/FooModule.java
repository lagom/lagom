/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import api.FooService;
import play.*;
import javax.inject.Inject;
import java.util.Date;
import java.io.*;

public class FooModule extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        bindService(FooService.class, FooServiceImpl.class);
        bind(FooOnStart.class).asEagerSingleton();
    }
}

class FooOnStart {

    @Inject
    public FooOnStart(Application app) {
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
