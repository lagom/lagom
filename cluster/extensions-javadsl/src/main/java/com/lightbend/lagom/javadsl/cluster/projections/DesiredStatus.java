/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.cluster.projections;

import akka.annotation.ApiMayChange;
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistryActor;
import scala.collection.JavaConverters;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

// TODO: generate using Immutables or Lombok instead.
@ApiMayChange
public final class DesiredStatus {

  private List<Projector> projectors;

  DesiredStatus(List<Projector> projectors) {
    this.projectors = projectors;
  }

  public static DesiredStatus asJava(ProjectorRegistryActor.DesiredStatus scala) {
    return new DesiredStatus(
        JavaConverters.seqAsJavaList(scala.projectors()).stream()
            .map(Projector::asJava)
            .collect(Collectors.toList()));
  }

  public List<Projector> getProjectors() {
    return projectors;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DesiredStatus that = (DesiredStatus) o;
    return Objects.equals(projectors, that.projectors);
  }

  @Override
  public int hashCode() {
    return Objects.hash(projectors);
  }

  @Override
  public String toString() {
    return "DesiredStatus{" + "projectors=" + projectors + '}';
  }
}
