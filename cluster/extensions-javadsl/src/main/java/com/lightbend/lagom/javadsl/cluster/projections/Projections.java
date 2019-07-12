/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.cluster.projections;

import akka.annotation.ApiMayChange;

import java.util.concurrent.CompletionStage;

/** TODO: docs */
@ApiMayChange
public interface Projections {

  CompletionStage<DesiredState> getStatus();

  // TODO: implement stop
  // TODO: implement start

}
