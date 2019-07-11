/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.cluster.projections;

import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistry;
import scala.collection.JavaConverters;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

// TODO: generate using Immutables or Lombok instead.
public final class Projector {

  private final String name;
  private final List<ProjectorWorker> workers;

  Projector(String name, List<ProjectorWorker> workers) {
    this.name = name;
    this.workers = workers;
  }

  static Projector asJava(ProjectorRegistry.Projector scala) {
    return new Projector(
        scala.name(),
        JavaConverters.seqAsJavaList(scala.workers()).stream()
            .map(ProjectorWorker::asJava)
            .collect(Collectors.toList()));
  }

  public String getName() {
    return name;
  }

  public List<ProjectorWorker> getWorkers() {
    return workers;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Projector projector = (Projector) o;
    return Objects.equals(name, projector.name) && Objects.equals(workers, projector.workers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, workers);
  }

  @Override
  public String toString() {
    return "Projector{" + "name='" + name + '\'' + ", workers=" + workers + '}';
  }
}
