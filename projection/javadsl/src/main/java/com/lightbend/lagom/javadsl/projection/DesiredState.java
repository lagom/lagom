/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.projection;

import akka.annotation.ApiMayChange;
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor;
import scala.collection.JavaConverters;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

// TODO: generate using Immutables or Lombok instead.
// https://github.com/lagom/lagom/issues/2053
@ApiMayChange
public final class DesiredState {

  private List<Projection> projections;

  DesiredState(List<Projection> projections) {
    this.projections = projections;
  }

  public static DesiredState asJava(ProjectionRegistryActor.DesiredState desiredState) {
    return new DesiredState(
        JavaConverters.seqAsJavaList(desiredState.projections()).stream()
            .map(Projection::asJava)
            .collect(Collectors.toList()));
  }

  public List<Projection> getProjections() {
    return projections;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DesiredState that = (DesiredState) o;
    return Objects.equals(projections, that.projections);
  }

  @Override
  public int hashCode() {
    return Objects.hash(projections);
  }

  @Override
  public String toString() {
    return "DesiredState{" + "projections=" + projections + '}';
  }
}
