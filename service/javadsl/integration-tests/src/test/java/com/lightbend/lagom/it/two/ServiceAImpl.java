/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.two;

import com.lightbend.lagom.javadsl.api.ServiceCall;
import javax.inject.Inject;

public class ServiceAImpl implements ServiceA {
  
    private final ServiceB serviceB;

    @Inject
    public ServiceAImpl(ServiceB serviceB) {
      this.serviceB = serviceB;
    }
    
    @Override
    public ServiceCall<String, String> helloA() {
        return req -> serviceB.helloB().invoke(req);
    }


}
