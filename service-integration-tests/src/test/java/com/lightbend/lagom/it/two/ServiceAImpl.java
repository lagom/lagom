/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.it.two;

import com.lightbend.lagom.javadsl.api.ServiceCall;
import javax.inject.Inject;

import akka.NotUsed;

public class ServiceAImpl implements ServiceA {
  
    private final ServiceB serviceB;

    @Inject
    public ServiceAImpl(ServiceB serviceB) {
      this.serviceB = serviceB;
    }
    
    @Override
    public ServiceCall<NotUsed, String, String> helloA() {
        return (id, req) -> serviceB.helloB().invoke(req);
    }


}
