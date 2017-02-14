/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api.tools;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;

public class AclServiceImpl implements AclService {

    @Override
    public ServiceCall<NotUsed, NotUsed> getMock(String id) {
        return request -> null;
    }

    @Override
    public ServiceCall<NotUsed, NotUsed> addMock() {
        return request -> null;
    }
}
