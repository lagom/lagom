/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.it.routers;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.*;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface AdditionalRoutersService extends Service {

  @Override
  default Descriptor descriptor() {
    return named("additional-routers");
  }
}
