/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.projection;

import akka.annotation.ApiMayChange;
import scala.collection.JavaConverters;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

// TODO: generate using Immutables or Lombok instead.
// https://github.com/lagom/lagom/issues/2053
@ApiMayChange
public final class State {

  // TODO: use PSequence instead
  private List<Projection> projections;

  State(List<Projection> projections) {
    this.projections = projections;
  }

  public static State asJava(com.lightbend.lagom.projection.State state) {
    return new State(
        JavaConverters.seqAsJavaList(state.projections()).stream()
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
    State that = (State) o;
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
