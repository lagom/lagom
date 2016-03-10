/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api.tools;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;

public class AclServiceImpl implements AclService {

    @Override
    public ServiceCall<String, NotUsed, NotUsed> getMock() {
        return (id, request) -> null;
    }

    @Override
    public ServiceCall<NotUsed, NotUsed, NotUsed> addMock() {
        return (id, request) -> null;
    }
}
