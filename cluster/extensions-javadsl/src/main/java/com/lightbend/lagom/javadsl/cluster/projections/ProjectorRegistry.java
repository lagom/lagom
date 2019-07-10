/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.cluster.projections;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistryImpl;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** TODO: docs */
public interface ProjectorRegistry {

  CompletionStage<
          Map<ProjectorRegistryImpl.ProjectionMetadata, ProjectorRegistryImpl.ProjectorStatus>>
      getStatus();

  // TODO: implement getStatus
  //       * will need to map internal Protocol(case classes) to external Java-friendly types/enums
  // TODO: implement stop
  // TODO: implement start

}
