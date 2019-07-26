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

  CompletionStage<State> getStatus();

  void stopAllWorkers(String projectionName) ;
  void stopWorker(String projectionName, String tagName) ;
  void startAllWorkers(String projectionName) ;
  void startWorker(String projectionName, String tagName) ;

}
