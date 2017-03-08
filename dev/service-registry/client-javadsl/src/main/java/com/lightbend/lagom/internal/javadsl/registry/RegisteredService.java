/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.registry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.net.URI;
import java.util.List;
import javax.annotation.Generated;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Immutable implementation of {@link AbstractRegisteredService}.
 * <p>
 * Use the builder to create immutable instances:
 * {@code RegisteredService.builder()}.
 * Use the static factory method to create immutable instances:
 * {@code RegisteredService.of()}.
 */
@SuppressWarnings("all")
@ParametersAreNonnullByDefault
@Generated({"Immutables.generator", "AbstractRegisteredService"})
@Immutable
public final class RegisteredService
    implements AbstractRegisteredService {
  private final String name;
  private final URI url;

  private RegisteredService(String name, URI url) {
    this.name = Preconditions.checkNotNull(name, "name");
    this.url = Preconditions.checkNotNull(url, "url");
  }

  private RegisteredService(RegisteredService original, String name, URI url) {
    this.name = name;
    this.url = url;
  }

  /**
   * @return The value of the {@code name} attribute
   */
  @JsonProperty
  @Override
  public String name() {
    return name;
  }

  /**
   * @return The value of the {@code url} attribute
   */
  @JsonProperty
  @Override
  public URI url() {
    return url;
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractRegisteredService#name() name} attribute.
   * A shallow reference equality check is used to prevent copying of the same value by returning {@code this}.
   * @param value A new value for name
   * @return A modified copy of the {@code this} object
   */
  public final RegisteredService withName(String value) {
    if (this.name == value) return this;
    String newValue = Preconditions.checkNotNull(value, "name");
    return new RegisteredService(this, newValue, this.url);
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractRegisteredService#url() url} attribute.
   * A shallow reference equality check is used to prevent copying of the same value by returning {@code this}.
   * @param value A new value for url
   * @return A modified copy of the {@code this} object
   */
  public final RegisteredService withUrl(URI value) {
    if (this.url == value) return this;
    URI newValue = Preconditions.checkNotNull(value, "url");
    return new RegisteredService(this, this.name, newValue);
  }

  /**
   * This instance is equal to all instances of {@code RegisteredService} that have equal attribute values.
   * @return {@code true} if {@code this} is equal to {@code another} instance
   */
  @Override
  public boolean equals(@Nullable Object another) {
    return this == another || another instanceof RegisteredService
        && equalTo((RegisteredService) another);
  }

  private boolean equalTo(RegisteredService another) {
    return name.equals(another.name)
        && url.equals(another.url);
  }

  /**
   * Computes a hash code from attributes: {@code name}, {@code url}.
   * @return hashCode value
   */
  @Override
  public int hashCode() {
    int h = 31;
    h = h * 17 + name.hashCode();
    h = h * 17 + url.hashCode();
    return h;
  }

  /**
   * Prints the immutable value {@code RegisteredService...} with all non-generated
   * and non-auxiliary attribute values.
   * @return A string representation of the value
   */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper("RegisteredService")
        .add("name", name)
        .add("url", url)
        .toString();
  }

  /**
   * Utility type used to correctly read immutable object from JSON representation.
   * @deprecated Do not use this type directly, it exists only for the <em>Jackson</em>-binding infrastructure
   */
  @Deprecated
  @JsonDeserialize
  static final class Json implements AbstractRegisteredService {
    @Nullable String name;
    @Nullable URI url;

    public void setName(String name) {
      this.name = name;
    }

    public void setUrl(URI url) {
      this.url = url;
    }
    @Override
    public String name() { throw new UnsupportedOperationException(); }
    @Override
    public URI url() { throw new UnsupportedOperationException(); }
  }

  /**
   * @param json A JSON-bindable data structure
   * @return An immutable value type
   * @deprecated Do not use this method directly, it exists only for the <em>Jackson</em>-binding infrastructure
   */
  @Deprecated
  @JsonCreator
  static RegisteredService fromJson(Json json) {
    RegisteredService.Builder builder = RegisteredService.builder();
    if (json.name != null) {
      builder.name(json.name);
    }
    if (json.url != null) {
      builder.url(json.url);
    }
    return builder.build();
  }

  /**
   * Construct a new immutable {@code RegisteredService} instance.
   * @param name The value for the {@code name} attribute
   * @param url The value for the {@code url} attribute
   * @return An immutable RegisteredService instance
   */
  public static RegisteredService of(String name, URI url) {
    return new RegisteredService(name, url);
  }

  /**
   * Creates an immutable copy of a {@link AbstractRegisteredService} value.
   * Uses accessors to get values to initialize the new immutable instance.
   * If an instance is already immutable, it is returned as is.
   * @param instance The instance to copy
   * @return A copied immutable RegisteredService instance
   */
  public static RegisteredService copyOf(AbstractRegisteredService instance) {
    if (instance instanceof RegisteredService) {
      return (RegisteredService) instance;
    }
    return RegisteredService.builder()
        .from(instance)
        .build();
  }

  /**
   * Creates a builder for {@link RegisteredService RegisteredService}.
   * @return A new RegisteredService builder
   */
  public static RegisteredService.Builder builder() {
    return new RegisteredService.Builder();
  }

  /**
   * Builds instances of type {@link RegisteredService RegisteredService}.
   * Initialize attributes and then invoke the {@link #build()} method to create an
   * immutable instance.
   * <p><em>{@code Builder} is not thread-safe and generally should not be stored in a field or collection,
   * but instead used immediately to create instances.</em>
   */
  @NotThreadSafe
  public static final class Builder {
    private static final long INIT_BIT_NAME = 0x1L;
    private static final long INIT_BIT_URL = 0x2L;
    private long initBits = 0x3;

    private @Nullable String name;
    private @Nullable URI url;

    private Builder() {}

    /**
     * Fill a builder with attribute values from the provided {@link AbstractRegisteredService} instance.
     * Regular attribute values will be replaced with those from the given instance.
     * Absent optional values will not replace present values.
     * @param instance The instance from which to copy values
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder from(AbstractRegisteredService instance) {
      Preconditions.checkNotNull(instance, "instance");
      name(instance.name());
      url(instance.url());
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractRegisteredService#name() name} attribute.
     * @param name The value for name 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder name(String name) {
      this.name = Preconditions.checkNotNull(name, "name");
      initBits &= ~INIT_BIT_NAME;
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractRegisteredService#url() url} attribute.
     * @param url The value for url 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder url(URI url) {
      this.url = Preconditions.checkNotNull(url, "url");
      initBits &= ~INIT_BIT_URL;
      return this;
    }
    /**
     * Builds a new {@link RegisteredService RegisteredService}.
     * @return An immutable instance of RegisteredService
     * @throws java.lang.IllegalStateException if any required attributes are missing
     */
    public RegisteredService build()
        throws IllegalStateException {
      checkRequiredAttributes(); return new RegisteredService(null, name, url);
    }

    private boolean nameIsSet() {
      return (initBits & INIT_BIT_NAME) == 0;
    }

    private boolean urlIsSet() {
      return (initBits & INIT_BIT_URL) == 0;
    }

    private void checkRequiredAttributes() throws IllegalStateException {
      if (initBits != 0) {
        throw new IllegalStateException(formatRequiredAttributesMessage());
      }
    }
    private String formatRequiredAttributesMessage() {
      List<String> attributes = Lists.newArrayList();
      if (!nameIsSet()) attributes.add("name");
      if (!urlIsSet()) attributes.add("url");
      return "Cannot build RegisteredService, some of required attributes are not set " + attributes;
    }
  }
}
