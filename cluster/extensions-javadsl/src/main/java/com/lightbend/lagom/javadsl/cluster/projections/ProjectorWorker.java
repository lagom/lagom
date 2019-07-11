/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.cluster.projections;

import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistry;

import java.util.Objects;

// TODO: generate using Immutables or Lombok instead.
public final class ProjectorWorker {

  private final String name;
  private final ProjectorRegistry.ProjectorStatus status;

  ProjectorWorker(String name, ProjectorRegistry.ProjectorStatus status) {
    this.name = name;
    this.status = status;
  }

  static ProjectorWorker asJava(ProjectorRegistry.ProjectorWorker scala) {
    return new ProjectorWorker(scala.name(), scala.status());
  }

  public String getName() {
    return name;
  }

  public ProjectorRegistry.ProjectorStatus getStatus() {
    return status;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProjectorWorker that = (ProjectorWorker) o;
    return Objects.equals(name, that.name) && Objects.equals(status, that.status);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, status);
  }

  @Override
  public String toString() {
    return "ProjectorWorker{" + "name='" + name + '\'' + ", status=" + status + '}';
  }
}
