/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.projection;

import akka.annotation.ApiMayChange;
import com.lightbend.lagom.projection.Status;

import java.util.Objects;

// TODO: generate using Immutables or Lombok instead.
// https://github.com/lagom/lagom/issues/2053
@ApiMayChange
public final class ProjectionWorker {

  private final String name;
  private final com.lightbend.lagom.projection.Status requested;
  private final com.lightbend.lagom.projection.Status observed;

  ProjectionWorker(
      String name,
      com.lightbend.lagom.projection.Status requested,
      com.lightbend.lagom.projection.Status observed) {
    this.name = name;
    this.requested = requested;
    this.observed = observed;
  }

  static ProjectionWorker asJava(com.lightbend.lagom.projection.Worker worker) {
    return new ProjectionWorker(worker.name(), worker.requestedStatus(), worker.observedStatus());
  }

  public String getName() {
    return name;
  }

  public Status getRequested() {
    return requested;
  }

  public Status getObserved() {
    return observed;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProjectionWorker that = (ProjectionWorker) o;
    return Objects.equals(name, that.name)
        && Objects.equals(requested, that.requested)
        && Objects.equals(observed, that.observed);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, requested, observed);
  }

  @Override
  public String toString() {
    return "ProjectionWorker{"
        + "name='"
        + name
        + '\''
        + ", requested="
        + requested
        + ", observed="
        + observed
        + '}';
  }
}
