/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.projection;

import akka.annotation.ApiMayChange;

import java.util.concurrent.CompletionStage;

// https://github.com/lagom/lagom/issues/2048
/** TODO: docs */
@ApiMayChange
public interface Projections {

  CompletionStage<DesiredState> getStatus();

  // https://github.com/lagom/lagom/issues/1744
  // TODO: implement stop
  // TODO: implement start

}
