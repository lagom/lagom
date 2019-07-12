/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.cluster.projections;

import akka.annotation.ApiMayChange;
import com.lightbend.lagom.internal.cluster.projections.ProjectionRegistry;

import java.util.Objects;

// TODO: generate using Immutables or Lombok instead.
@ApiMayChange
public final class ProjectionWorker {

  private final String name;
  private final ProjectionRegistry.WorkerStatus status;

  ProjectionWorker(String name, ProjectionRegistry.WorkerStatus status) {
    this.name = name;
    this.status = status;
  }

  static ProjectionWorker asJava(ProjectionRegistry.ProjectionWorker worker) {
    return new ProjectionWorker(worker.name(), worker.status());
  }

  public String getName() {
    return name;
  }

  public ProjectionRegistry.WorkerStatus getStatus() {
    return status;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProjectionWorker that = (ProjectionWorker) o;
    return Objects.equals(name, that.name) && Objects.equals(status, that.status);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, status);
  }

  @Override
  public String toString() {
    return "ProjectionWorker{" + "name='" + name + '\'' + ", status=" + status + '}';
  }
}
