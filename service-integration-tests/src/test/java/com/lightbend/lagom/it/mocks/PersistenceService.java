/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.*;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface PersistenceService extends Service {

    ServiceCall<NotUsed, String> checkInjected();
    
    ServiceCall<NotUsed, String> checkCassandraSession();

    @Override
    default Descriptor descriptor() {
        return named("persistence").with(
            call(this::checkInjected),
            call(this::checkCassandraSession)
        );
    }
}
