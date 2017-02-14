/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.server.status;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Longs;
import java.time.Instant;
import java.util.List;
import javax.annotation.Generated;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Immutable implementation of {@link AbstractCircuitBreakerStatus}.
 * <p>
 * Use the builder to create immutable instances:
 * {@code CircuitBreakerStatus.builder()}.
 */
@SuppressWarnings("all")
@ParametersAreNonnullByDefault
@Generated({"Immutables.generator", "AbstractCircuitBreakerStatus"})
@Immutable
public final class CircuitBreakerStatus
    implements AbstractCircuitBreakerStatus {
  private final String id;
  private final Instant timestamp;
  private final String state;
  private final long totalSuccessCount;
  private final long totalFailureCount;
  private final Latency latencyMicros;
  private final double throughputOneMinute;
  private final double failedThroughputOneMinute;

  private CircuitBreakerStatus(CircuitBreakerStatus.Builder builder) {
    this.id = builder.id;
    this.state = builder.state;
    this.totalSuccessCount = builder.totalSuccessCount;
    this.totalFailureCount = builder.totalFailureCount;
    this.latencyMicros = builder.latencyMicros;
    this.throughputOneMinute = builder.throughputOneMinute;
    this.failedThroughputOneMinute = builder.failedThroughputOneMinute;
    this.timestamp = builder.timestamp != null
        ? builder.timestamp
        : Preconditions.checkNotNull(AbstractCircuitBreakerStatus.super.getTimestamp(), "timestamp");
  }

  private CircuitBreakerStatus(
      String id,
      Instant timestamp,
      String state,
      long totalSuccessCount,
      long totalFailureCount,
      Latency latencyMicros,
      double throughputOneMinute,
      double failedThroughputOneMinute) {
    this.id = id;
    this.timestamp = timestamp;
    this.state = state;
    this.totalSuccessCount = totalSuccessCount;
    this.totalFailureCount = totalFailureCount;
    this.latencyMicros = latencyMicros;
    this.throughputOneMinute = throughputOneMinute;
    this.failedThroughputOneMinute = failedThroughputOneMinute;
  }

  /**
   * Circuit breaker identifier.
   */
  @JsonProperty
  @Override
  public String getId() {
    return id;
  }

  /**
   * @return The value of the {@code timestamp} attribute
   */
  @JsonProperty
  @Override
  public Instant getTimestamp() {
    return timestamp;
  }

  /**
   * Circuit breaker state; closed, open, half-open.
   */
  @JsonProperty
  @Override
  public String getState() {
    return state;
  }

  /**
   * Total number of successful calls.
   */
  @JsonProperty
  @Override
  public long getTotalSuccessCount() {
    return totalSuccessCount;
  }

  /**
   * Total number of failed calls.
   */
  @JsonProperty
  @Override
  public long getTotalFailureCount() {
    return totalFailureCount;
  }

  /**
   * Latency distribution for Exponentially Decaying Reservoir. Time unit is
   * microseconds.
   */
  @JsonProperty
  @Override
  public Latency getLatencyMicros() {
    return latencyMicros;
  }

  /**
   * Total (successful + failed) calls per second for the last minute.
   */
  @JsonProperty
  @Override
  public double getThroughputOneMinute() {
    return throughputOneMinute;
  }

  /**
   * Failed calls per second for the last minute.
   */
  @JsonProperty
  @Override
  public double getFailedThroughputOneMinute() {
    return failedThroughputOneMinute;
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractCircuitBreakerStatus#getId() id} attribute.
   * A shallow reference equality check is used to prevent copying of the same value by returning {@code this}.
   * @param value A new value for id
   * @return A modified copy of the {@code this} object
   */
  public final CircuitBreakerStatus withId(String value) {
    if (this.id == value) return this;
    String newValue = Preconditions.checkNotNull(value, "id");
    return new CircuitBreakerStatus(
        newValue,
        this.timestamp,
        this.state,
        this.totalSuccessCount,
        this.totalFailureCount,
        this.latencyMicros,
        this.throughputOneMinute,
        this.failedThroughputOneMinute);
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractCircuitBreakerStatus#getTimestamp() timestamp} attribute.
   * A shallow reference equality check is used to prevent copying of the same value by returning {@code this}.
   * @param value A new value for timestamp
   * @return A modified copy of the {@code this} object
   */
  public final CircuitBreakerStatus withTimestamp(Instant value) {
    if (this.timestamp == value) return this;
    Instant newValue = Preconditions.checkNotNull(value, "timestamp");
    return new CircuitBreakerStatus(
        this.id,
        newValue,
        this.state,
        this.totalSuccessCount,
        this.totalFailureCount,
        this.latencyMicros,
        this.throughputOneMinute,
        this.failedThroughputOneMinute);
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractCircuitBreakerStatus#getState() state} attribute.
   * A shallow reference equality check is used to prevent copying of the same value by returning {@code this}.
   * @param value A new value for state
   * @return A modified copy of the {@code this} object
   */
  public final CircuitBreakerStatus withState(String value) {
    if (this.state == value) return this;
    String newValue = Preconditions.checkNotNull(value, "state");
    return new CircuitBreakerStatus(
        this.id,
        this.timestamp,
        newValue,
        this.totalSuccessCount,
        this.totalFailureCount,
        this.latencyMicros,
        this.throughputOneMinute,
        this.failedThroughputOneMinute);
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractCircuitBreakerStatus#getTotalSuccessCount() totalSuccessCount} attribute.
   * A value equality check is used to prevent copying of the same value by returning {@code this}.
   * @param value A new value for totalSuccessCount
   * @return A modified copy of the {@code this} object
   */
  public final CircuitBreakerStatus withTotalSuccessCount(long value) {
    if (this.totalSuccessCount == value) return this;
    long newValue = value;
    return new CircuitBreakerStatus(
        this.id,
        this.timestamp,
        this.state,
        newValue,
        this.totalFailureCount,
        this.latencyMicros,
        this.throughputOneMinute,
        this.failedThroughputOneMinute);
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractCircuitBreakerStatus#getTotalFailureCount() totalFailureCount} attribute.
   * A value equality check is used to prevent copying of the same value by returning {@code this}.
   * @param value A new value for totalFailureCount
   * @return A modified copy of the {@code this} object
   */
  public final CircuitBreakerStatus withTotalFailureCount(long value) {
    if (this.totalFailureCount == value) return this;
    long newValue = value;
    return new CircuitBreakerStatus(
        this.id,
        this.timestamp,
        this.state,
        this.totalSuccessCount,
        newValue,
        this.latencyMicros,
        this.throughputOneMinute,
        this.failedThroughputOneMinute);
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractCircuitBreakerStatus#getLatencyMicros() latencyMicros} attribute.
   * A shallow reference equality check is used to prevent copying of the same value by returning {@code this}.
   * @param value A new value for latencyMicros
   * @return A modified copy of the {@code this} object
   */
  public final CircuitBreakerStatus withLatencyMicros(Latency value) {
    if (this.latencyMicros == value) return this;
    Latency newValue = Preconditions.checkNotNull(value, "latencyMicros");
    return new CircuitBreakerStatus(
        this.id,
        this.timestamp,
        this.state,
        this.totalSuccessCount,
        this.totalFailureCount,
        newValue,
        this.throughputOneMinute,
        this.failedThroughputOneMinute);
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractCircuitBreakerStatus#getThroughputOneMinute() throughputOneMinute} attribute.
   * @param value A new value for throughputOneMinute
   * @return A modified copy of the {@code this} object
   */
  public final CircuitBreakerStatus withThroughputOneMinute(double value) {
    double newValue = value;
    return new CircuitBreakerStatus(
        this.id,
        this.timestamp,
        this.state,
        this.totalSuccessCount,
        this.totalFailureCount,
        this.latencyMicros,
        newValue,
        this.failedThroughputOneMinute);
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractCircuitBreakerStatus#getFailedThroughputOneMinute() failedThroughputOneMinute} attribute.
   * @param value A new value for failedThroughputOneMinute
   * @return A modified copy of the {@code this} object
   */
  public final CircuitBreakerStatus withFailedThroughputOneMinute(double value) {
    double newValue = value;
    return new CircuitBreakerStatus(
        this.id,
        this.timestamp,
        this.state,
        this.totalSuccessCount,
        this.totalFailureCount,
        this.latencyMicros,
        this.throughputOneMinute,
        newValue);
  }

  /**
   * This instance is equal to all instances of {@code CircuitBreakerStatus} that have equal attribute values.
   * @return {@code true} if {@code this} is equal to {@code another} instance
   */
  @Override
  public boolean equals(@Nullable Object another) {
    if (this == another) return true;
    return another instanceof CircuitBreakerStatus
        && equalTo((CircuitBreakerStatus) another);
  }

  private boolean equalTo(CircuitBreakerStatus another) {
    return id.equals(another.id)
        && timestamp.equals(another.timestamp)
        && state.equals(another.state)
        && totalSuccessCount == another.totalSuccessCount
        && totalFailureCount == another.totalFailureCount
        && latencyMicros.equals(another.latencyMicros)
        && Double.doubleToLongBits(throughputOneMinute) == Double.doubleToLongBits(another.throughputOneMinute)
        && Double.doubleToLongBits(failedThroughputOneMinute) == Double.doubleToLongBits(another.failedThroughputOneMinute);
  }

  /**
   * Computes a hash code from attributes: {@code id}, {@code timestamp}, {@code state}, {@code totalSuccessCount}, {@code totalFailureCount}, {@code latencyMicros}, {@code throughputOneMinute}, {@code failedThroughputOneMinute}.
   * @return hashCode value
   */
  @Override
  public int hashCode() {
    int h = 31;
    h = h * 17 + id.hashCode();
    h = h * 17 + timestamp.hashCode();
    h = h * 17 + state.hashCode();
    h = h * 17 + Longs.hashCode(totalSuccessCount);
    h = h * 17 + Longs.hashCode(totalFailureCount);
    h = h * 17 + latencyMicros.hashCode();
    h = h * 17 + Doubles.hashCode(throughputOneMinute);
    h = h * 17 + Doubles.hashCode(failedThroughputOneMinute);
    return h;
  }

  /**
   * Prints the immutable value {@code CircuitBreakerStatus...} with all non-generated
   * and non-auxiliary attribute values.
   * @return A string representation of the value
   */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper("CircuitBreakerStatus")
        .add("id", id)
        .add("timestamp", timestamp)
        .add("state", state)
        .add("totalSuccessCount", totalSuccessCount)
        .add("totalFailureCount", totalFailureCount)
        .add("latencyMicros", latencyMicros)
        .add("throughputOneMinute", throughputOneMinute)
        .add("failedThroughputOneMinute", failedThroughputOneMinute)
        .toString();
  }

  /**
   * Utility type used to correctly read immutable object from JSON representation.
   * @deprecated Do not use this type directly, it exists only for the <em>Jackson</em>-binding infrastructure
   */
  @Deprecated
  @JsonDeserialize
  static final class Json implements AbstractCircuitBreakerStatus {
    @Nullable String id;
    @Nullable Instant timestamp;
    @Nullable String state;
    @Nullable Long totalSuccessCount;
    @Nullable Long totalFailureCount;
    @Nullable Latency latencyMicros;
    @Nullable java.lang.Double throughputOneMinute;
    @Nullable java.lang.Double failedThroughputOneMinute;

    public void setId(String id) {
      this.id = id;
    }

    public void setTimestamp(Instant timestamp) {
      this.timestamp = timestamp;
    }

    public void setState(String state) {
      this.state = state;
    }

    public void setTotalSuccessCount(long totalSuccessCount) {
      this.totalSuccessCount = totalSuccessCount;
    }

    public void setTotalFailureCount(long totalFailureCount) {
      this.totalFailureCount = totalFailureCount;
    }

    public void setLatencyMicros(Latency latencyMicros) {
      this.latencyMicros = latencyMicros;
    }

    public void setThroughputOneMinute(double throughputOneMinute) {
      this.throughputOneMinute = throughputOneMinute;
    }

    public void setFailedThroughputOneMinute(double failedThroughputOneMinute) {
      this.failedThroughputOneMinute = failedThroughputOneMinute;
    }
    @Override
    public String getId() { throw new UnsupportedOperationException(); }
    @Override
    public String getState() { throw new UnsupportedOperationException(); }
    @Override
    public long getTotalSuccessCount() { throw new UnsupportedOperationException(); }
    @Override
    public long getTotalFailureCount() { throw new UnsupportedOperationException(); }
    @Override
    public Latency getLatencyMicros() { throw new UnsupportedOperationException(); }
    @Override
    public double getThroughputOneMinute() { throw new UnsupportedOperationException(); }
    @Override
    public double getFailedThroughputOneMinute() { throw new UnsupportedOperationException(); }
  }

  /**
   * @param json A JSON-bindable data structure
   * @return An immutable value type
   * @deprecated Do not use this method directly, it exists only for the <em>Jackson</em>-binding infrastructure
   */
  @Deprecated
  @JsonCreator
  static CircuitBreakerStatus fromJson(Json json) {
    CircuitBreakerStatus.Builder builder = CircuitBreakerStatus.builder();
    if (json.id != null) {
      builder.id(json.id);
    }
    if (json.timestamp != null) {
      builder.timestamp(json.timestamp);
    }
    if (json.state != null) {
      builder.state(json.state);
    }
    if (json.totalSuccessCount != null) {
      builder.totalSuccessCount(json.totalSuccessCount);
    }
    if (json.totalFailureCount != null) {
      builder.totalFailureCount(json.totalFailureCount);
    }
    if (json.latencyMicros != null) {
      builder.latencyMicros(json.latencyMicros);
    }
    if (json.throughputOneMinute != null) {
      builder.throughputOneMinute(json.throughputOneMinute);
    }
    if (json.failedThroughputOneMinute != null) {
      builder.failedThroughputOneMinute(json.failedThroughputOneMinute);
    }
    return builder.build();
  }

  /**
   * Creates an immutable copy of a {@link AbstractCircuitBreakerStatus} value.
   * Uses accessors to get values to initialize the new immutable instance.
   * If an instance is already immutable, it is returned as is.
   * @param instance The instance to copy
   * @return A copied immutable CircuitBreakerStatus instance
   */
  public static CircuitBreakerStatus copyOf(AbstractCircuitBreakerStatus instance) {
    if (instance instanceof CircuitBreakerStatus) {
      return (CircuitBreakerStatus) instance;
    }
    return CircuitBreakerStatus.builder()
        .from(instance)
        .build();
  }

  /**
   * Creates a builder for {@link com.lightbend.lagom.javadsl.server.status.CircuitBreakerStatus CircuitBreakerStatus}.
   * @return A new CircuitBreakerStatus builder
   */
  public static CircuitBreakerStatus.Builder builder() {
    return new CircuitBreakerStatus.Builder();
  }

  /**
   * Builds instances of type {@link com.lightbend.lagom.javadsl.server.status.CircuitBreakerStatus CircuitBreakerStatus}.
   * Initialize attributes and then invoke the {@link #build()} method to create an
   * immutable instance.
   * <p><em>{@code Builder} is not thread-safe and generally should not be stored in a field or collection,
   * but instead used immediately to create instances.</em>
   */
  @NotThreadSafe
  public static final class Builder {
    private static final long INIT_BIT_ID = 0x1L;
    private static final long INIT_BIT_STATE = 0x2L;
    private static final long INIT_BIT_TOTAL_SUCCESS_COUNT = 0x4L;
    private static final long INIT_BIT_TOTAL_FAILURE_COUNT = 0x8L;
    private static final long INIT_BIT_LATENCY_MICROS = 0x10L;
    private static final long INIT_BIT_THROUGHPUT_ONE_MINUTE = 0x20L;
    private static final long INIT_BIT_FAILED_THROUGHPUT_ONE_MINUTE = 0x40L;
    private long initBits = 0x7f;

    private @Nullable String id;
    private @Nullable Instant timestamp;
    private @Nullable String state;
    private long totalSuccessCount;
    private long totalFailureCount;
    private @Nullable Latency latencyMicros;
    private double throughputOneMinute;
    private double failedThroughputOneMinute;

    private Builder() {}

    /**
     * Fill a builder with attribute values from the provided {@link AbstractCircuitBreakerStatus} instance.
     * Regular attribute values will be replaced with those from the given instance.
     * Absent optional values will not replace present values.
     * @param instance The instance from which to copy values
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder from(AbstractCircuitBreakerStatus instance) {
      Preconditions.checkNotNull(instance, "instance");
      id(instance.getId());
      timestamp(instance.getTimestamp());
      state(instance.getState());
      totalSuccessCount(instance.getTotalSuccessCount());
      totalFailureCount(instance.getTotalFailureCount());
      latencyMicros(instance.getLatencyMicros());
      throughputOneMinute(instance.getThroughputOneMinute());
      failedThroughputOneMinute(instance.getFailedThroughputOneMinute());
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractCircuitBreakerStatus#getId() id} attribute.
     * @param id The value for id 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder id(String id) {
      this.id = Preconditions.checkNotNull(id, "id");
      initBits &= ~INIT_BIT_ID;
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractCircuitBreakerStatus#getTimestamp() timestamp} attribute.
     * <p><em>If not set, this attribute will have a default value as returned by the initializer of {@link AbstractCircuitBreakerStatus#getTimestamp() timestamp}.</em>
     * @param timestamp The value for timestamp 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder timestamp(Instant timestamp) {
      this.timestamp = Preconditions.checkNotNull(timestamp, "timestamp");
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractCircuitBreakerStatus#getState() state} attribute.
     * @param state The value for state 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder state(String state) {
      this.state = Preconditions.checkNotNull(state, "state");
      initBits &= ~INIT_BIT_STATE;
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractCircuitBreakerStatus#getTotalSuccessCount() totalSuccessCount} attribute.
     * @param totalSuccessCount The value for totalSuccessCount 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder totalSuccessCount(long totalSuccessCount) {
      this.totalSuccessCount = totalSuccessCount;
      initBits &= ~INIT_BIT_TOTAL_SUCCESS_COUNT;
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractCircuitBreakerStatus#getTotalFailureCount() totalFailureCount} attribute.
     * @param totalFailureCount The value for totalFailureCount 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder totalFailureCount(long totalFailureCount) {
      this.totalFailureCount = totalFailureCount;
      initBits &= ~INIT_BIT_TOTAL_FAILURE_COUNT;
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractCircuitBreakerStatus#getLatencyMicros() latencyMicros} attribute.
     * @param latencyMicros The value for latencyMicros 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder latencyMicros(Latency latencyMicros) {
      this.latencyMicros = Preconditions.checkNotNull(latencyMicros, "latencyMicros");
      initBits &= ~INIT_BIT_LATENCY_MICROS;
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractCircuitBreakerStatus#getThroughputOneMinute() throughputOneMinute} attribute.
     * @param throughputOneMinute The value for throughputOneMinute 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder throughputOneMinute(double throughputOneMinute) {
      this.throughputOneMinute = throughputOneMinute;
      initBits &= ~INIT_BIT_THROUGHPUT_ONE_MINUTE;
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractCircuitBreakerStatus#getFailedThroughputOneMinute() failedThroughputOneMinute} attribute.
     * @param failedThroughputOneMinute The value for failedThroughputOneMinute 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder failedThroughputOneMinute(double failedThroughputOneMinute) {
      this.failedThroughputOneMinute = failedThroughputOneMinute;
      initBits &= ~INIT_BIT_FAILED_THROUGHPUT_ONE_MINUTE;
      return this;
    }
    /**
     * Builds a new {@link com.lightbend.lagom.javadsl.server.status.CircuitBreakerStatus CircuitBreakerStatus}.
     * @return An immutable instance of CircuitBreakerStatus
     * @throws java.lang.IllegalStateException if any required attributes are missing
     */
    public CircuitBreakerStatus build()
        throws IllegalStateException {
      checkRequiredAttributes(); return new CircuitBreakerStatus(this);
    }

    private boolean idIsSet() {
      return (initBits & INIT_BIT_ID) == 0;
    }

    private boolean stateIsSet() {
      return (initBits & INIT_BIT_STATE) == 0;
    }

    private boolean totalSuccessCountIsSet() {
      return (initBits & INIT_BIT_TOTAL_SUCCESS_COUNT) == 0;
    }

    private boolean totalFailureCountIsSet() {
      return (initBits & INIT_BIT_TOTAL_FAILURE_COUNT) == 0;
    }

    private boolean latencyMicrosIsSet() {
      return (initBits & INIT_BIT_LATENCY_MICROS) == 0;
    }

    private boolean throughputOneMinuteIsSet() {
      return (initBits & INIT_BIT_THROUGHPUT_ONE_MINUTE) == 0;
    }

    private boolean failedThroughputOneMinuteIsSet() {
      return (initBits & INIT_BIT_FAILED_THROUGHPUT_ONE_MINUTE) == 0;
    }

    private void checkRequiredAttributes() throws IllegalStateException {
      if (initBits != 0) {
        throw new IllegalStateException(formatRequiredAttributesMessage());
      }
    }
    private String formatRequiredAttributesMessage() {
      List<String> attributes = Lists.newArrayList();
      if (!idIsSet()) attributes.add("id");
      if (!stateIsSet()) attributes.add("state");
      if (!totalSuccessCountIsSet()) attributes.add("totalSuccessCount");
      if (!totalFailureCountIsSet()) attributes.add("totalFailureCount");
      if (!latencyMicrosIsSet()) attributes.add("latencyMicros");
      if (!throughputOneMinuteIsSet()) attributes.add("throughputOneMinute");
      if (!failedThroughputOneMinuteIsSet()) attributes.add("failedThroughputOneMinute");
      return "Cannot build CircuitBreakerStatus, some of required attributes are not set " + attributes;
    }
  }
}
