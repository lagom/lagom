/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.cluster.projections;

import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistryImpl;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/** TODO: docs */
public interface ProjectorRegistry {

  CompletionStage<
          Map<ProjectorRegistryImpl.ProjectionMetadata, ProjectorRegistryImpl.ProjectorStatus>>
      getStatus();

  // TODO: implement stop
  // TODO: implement start

}
