/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;

import static com.lightbend.lagom.javadsl.api.Service.*;

// non-public on purpose. This is an invalid Service description for testing purposes.
interface NotPublicInterfaceService extends Service {

  ServiceCall<String, String> helloA();

  default Descriptor descriptor() {
    return named("/serviceA").withCalls(
        call(this::helloA)
    );
  }

}
