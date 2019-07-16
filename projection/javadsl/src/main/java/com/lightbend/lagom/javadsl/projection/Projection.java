/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.projection;

import akka.annotation.ApiMayChange;
import com.lightbend.lagom.internal.projection.ProjectionRegistry;
import scala.collection.JavaConverters;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

// TODO: generate using Immutables or Lombok instead.
// https://github.com/lagom/lagom/issues/2053
@ApiMayChange
public final class Projection {

  private final String name;
  private final List<ProjectionWorker> workers;

  Projection(String name, List<ProjectionWorker> workers) {
    this.name = name;
    this.workers = workers;
  }

  static Projection asJava(ProjectionRegistry.Projection projection) {
    return new Projection(
        projection.name(),
        JavaConverters.seqAsJavaList(projection.workers()).stream()
            .map(ProjectionWorker::asJava)
            .collect(Collectors.toList()));
  }

  public String getName() {
    return name;
  }

  public List<ProjectionWorker> getWorkers() {
    return workers;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Projection projection = (Projection) o;
    return Objects.equals(name, projection.name) && Objects.equals(workers, projection.workers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, workers);
  }

  @Override
  public String toString() {
    return "Projection{" + "name='" + name + '\'' + ", workers=" + workers + '}';
  }
}
