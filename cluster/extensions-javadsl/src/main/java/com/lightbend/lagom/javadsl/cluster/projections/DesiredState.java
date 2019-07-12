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
public final class DesiredState {

  private List<Projector> projectors;

  DesiredState(List<Projector> projectors) {
    this.projectors = projectors;
  }

  public static DesiredState asJava(ProjectorRegistryActor.DesiredState desiredState) {
    return new DesiredState(
        JavaConverters.seqAsJavaList(desiredState.projectors()).stream()
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
    DesiredState that = (DesiredState) o;
    return Objects.equals(projectors, that.projectors);
  }

  @Override
  public int hashCode() {
    return Objects.hash(projectors);
  }

  @Override
  public String toString() {
    return "DesiredState{" + "projectors=" + projectors + '}';
  }
}
