/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.registry;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

public final class RegisteredService {
  private final String name;
  private final URI url;
  private final Optional<String> portName;

  private RegisteredService(String name, URI url, Optional<String> portName) {
    this.name = Preconditions.checkNotNull(name, "name");
    this.url = Preconditions.checkNotNull(url, "url");
    this.portName = portName;
  }

  @JsonProperty
  public String getName() {
    return name;
  }

  @JsonProperty
  public URI getUrl() {
    return url;
  }

  @JsonProperty
  public Optional<String> getPortName() {
    return portName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RegisteredService that = (RegisteredService) o;
    return Objects.equals(name, that.name)
        && Objects.equals(url, that.url)
        && Objects.equals(portName, that.portName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, url, portName);
  }

  @Override
  public String toString() {
    return "RegisteredService{"
        + "name='"
        + name
        + '\''
        + ", url="
        + url
        + ", portName="
        + portName
        + '}';
  }

  /**
   * Construct a new immutable {@code RegisteredService} instance.
   *
   * @param name The value for the {@code name} attribute
   * @param url The value for the {@code url} attribute
   * @return An immutable RegisteredService instance
   */
  public static RegisteredService of(String name, URI url, Optional<String> portName) {
    return new RegisteredService(name, url, portName);
  }
}
