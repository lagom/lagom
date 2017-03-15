/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.api.tools.tests.javadsl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;

public class NoAclServiceImpl implements NoAclService {

    @Override
    public ServiceCall<NotUsed, NotUsed> getMock(String id) {
        return request -> null;
    }
}
