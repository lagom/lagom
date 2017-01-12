/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
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
import java.util.List;
import javax.annotation.Generated;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Immutable implementation of {@link AbstractLatency}.
 * <p>
 * Use the builder to create immutable instances:
 * {@code Latency.builder()}.
 */
@SuppressWarnings("all")
@ParametersAreNonnullByDefault
@Generated({"Immutables.generator", "AbstractLatency"})
@Immutable
public final class Latency implements AbstractLatency {
  private final double median;
  private final double percentile98th;
  private final double percentile99th;
  private final double percentile999th;
  private final double mean;
  private final long min;
  private final long max;

  private Latency(
      double median,
      double percentile98th,
      double percentile99th,
      double percentile999th,
      double mean,
      long min,
      long max) {
    this.median = median;
    this.percentile98th = percentile98th;
    this.percentile99th = percentile99th;
    this.percentile999th = percentile999th;
    this.mean = mean;
    this.min = min;
    this.max = max;
  }

  /**
   * @return the median value in the distribution
   */
  @JsonProperty
  @Override
  public double getMedian() {
    return median;
  }

  /**
   * @return the value at the 98th percentile
   */
  @JsonProperty
  @Override
  public double getPercentile98th() {
    return percentile98th;
  }

  /**
   * @return the value at the 99th percentile
   */
  @JsonProperty
  @Override
  public double getPercentile99th() {
    return percentile99th;
  }

  /**
   * @return the value at the 99.9th percentile
   */
  @JsonProperty
  @Override
  public double getPercentile999th() {
    return percentile999th;
  }

  /**
   * @return the arithmetic mean
   */
  @JsonProperty
  @Override
  public double getMean() {
    return mean;
  }

  /**
   * @return the lowest value
   */
  @JsonProperty
  @Override
  public long getMin() {
    return min;
  }

  /**
   * @return the highest value
   */
  @JsonProperty
  @Override
  public long getMax() {
    return max;
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractLatency#getMedian() median} attribute.
   * @param value A new value for median
   * @return A modified copy of the {@code this} object
   */
  public final Latency withMedian(double value) {
    double newValue = value;
    return new Latency(
        newValue,
        this.percentile98th,
        this.percentile99th,
        this.percentile999th,
        this.mean,
        this.min,
        this.max);
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractLatency#getPercentile98th() percentile98th} attribute.
   * @param value A new value for percentile98th
   * @return A modified copy of the {@code this} object
   */
  public final Latency withPercentile98th(double value) {
    double newValue = value;
    return new Latency(this.median, newValue, this.percentile99th, this.percentile999th, this.mean, this.min, this.max);
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractLatency#getPercentile99th() percentile99th} attribute.
   * @param value A new value for percentile99th
   * @return A modified copy of the {@code this} object
   */
  public final Latency withPercentile99th(double value) {
    double newValue = value;
    return new Latency(this.median, this.percentile98th, newValue, this.percentile999th, this.mean, this.min, this.max);
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractLatency#getPercentile999th() percentile999th} attribute.
   * @param value A new value for percentile999th
   * @return A modified copy of the {@code this} object
   */
  public final Latency withPercentile999th(double value) {
    double newValue = value;
    return new Latency(this.median, this.percentile98th, this.percentile99th, newValue, this.mean, this.min, this.max);
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractLatency#getMean() mean} attribute.
   * @param value A new value for mean
   * @return A modified copy of the {@code this} object
   */
  public final Latency withMean(double value) {
    double newValue = value;
    return new Latency(
        this.median,
        this.percentile98th,
        this.percentile99th,
        this.percentile999th,
        newValue,
        this.min,
        this.max);
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractLatency#getMin() min} attribute.
   * A value equality check is used to prevent copying of the same value by returning {@code this}.
   * @param value A new value for min
   * @return A modified copy of the {@code this} object
   */
  public final Latency withMin(long value) {
    if (this.min == value) return this;
    long newValue = value;
    return new Latency(
        this.median,
        this.percentile98th,
        this.percentile99th,
        this.percentile999th,
        this.mean,
        newValue,
        this.max);
  }

  /**
   * Copy the current immutable object by setting a value for the {@link AbstractLatency#getMax() max} attribute.
   * A value equality check is used to prevent copying of the same value by returning {@code this}.
   * @param value A new value for max
   * @return A modified copy of the {@code this} object
   */
  public final Latency withMax(long value) {
    if (this.max == value) return this;
    long newValue = value;
    return new Latency(
        this.median,
        this.percentile98th,
        this.percentile99th,
        this.percentile999th,
        this.mean,
        this.min,
        newValue);
  }

  /**
   * This instance is equal to all instances of {@code Latency} that have equal attribute values.
   * @return {@code true} if {@code this} is equal to {@code another} instance
   */
  @Override
  public boolean equals(@Nullable Object another) {
    if (this == another) return true;
    return another instanceof Latency
        && equalTo((Latency) another);
  }

  private boolean equalTo(Latency another) {
    return Double.doubleToLongBits(median) == Double.doubleToLongBits(another.median)
        && Double.doubleToLongBits(percentile98th) == Double.doubleToLongBits(another.percentile98th)
        && Double.doubleToLongBits(percentile99th) == Double.doubleToLongBits(another.percentile99th)
        && Double.doubleToLongBits(percentile999th) == Double.doubleToLongBits(another.percentile999th)
        && Double.doubleToLongBits(mean) == Double.doubleToLongBits(another.mean)
        && min == another.min
        && max == another.max;
  }

  /**
   * Computes a hash code from attributes: {@code median}, {@code percentile98th}, {@code percentile99th}, {@code percentile999th}, {@code mean}, {@code min}, {@code max}.
   * @return hashCode value
   */
  @Override
  public int hashCode() {
    int h = 31;
    h = h * 17 + Doubles.hashCode(median);
    h = h * 17 + Doubles.hashCode(percentile98th);
    h = h * 17 + Doubles.hashCode(percentile99th);
    h = h * 17 + Doubles.hashCode(percentile999th);
    h = h * 17 + Doubles.hashCode(mean);
    h = h * 17 + Longs.hashCode(min);
    h = h * 17 + Longs.hashCode(max);
    return h;
  }

  /**
   * Prints the immutable value {@code Latency...} with all non-generated
   * and non-auxiliary attribute values.
   * @return A string representation of the value
   */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper("Latency")
        .add("median", median)
        .add("percentile98th", percentile98th)
        .add("percentile99th", percentile99th)
        .add("percentile999th", percentile999th)
        .add("mean", mean)
        .add("min", min)
        .add("max", max)
        .toString();
  }

  /**
   * Utility type used to correctly read immutable object from JSON representation.
   * @deprecated Do not use this type directly, it exists only for the <em>Jackson</em>-binding infrastructure
   */
  @Deprecated
  @JsonDeserialize
  static final class Json implements AbstractLatency {
    @Nullable java.lang.Double median;
    @Nullable java.lang.Double percentile98th;
    @Nullable java.lang.Double percentile99th;
    @Nullable java.lang.Double percentile999th;
    @Nullable java.lang.Double mean;
    @Nullable Long min;
    @Nullable Long max;

    public void setMedian(double median) {
      this.median = median;
    }

    public void setPercentile98th(double percentile98th) {
      this.percentile98th = percentile98th;
    }

    public void setPercentile99th(double percentile99th) {
      this.percentile99th = percentile99th;
    }

    public void setPercentile999th(double percentile999th) {
      this.percentile999th = percentile999th;
    }

    public void setMean(double mean) {
      this.mean = mean;
    }

    public void setMin(long min) {
      this.min = min;
    }

    public void setMax(long max) {
      this.max = max;
    }
    @Override
    public double getMedian() { throw new UnsupportedOperationException(); }
    @Override
    public double getPercentile98th() { throw new UnsupportedOperationException(); }
    @Override
    public double getPercentile99th() { throw new UnsupportedOperationException(); }
    @Override
    public double getPercentile999th() { throw new UnsupportedOperationException(); }
    @Override
    public double getMean() { throw new UnsupportedOperationException(); }
    @Override
    public long getMin() { throw new UnsupportedOperationException(); }
    @Override
    public long getMax() { throw new UnsupportedOperationException(); }
  }

  /**
   * @param json A JSON-bindable data structure
   * @return An immutable value type
   * @deprecated Do not use this method directly, it exists only for the <em>Jackson</em>-binding infrastructure
   */
  @Deprecated
  @JsonCreator
  static Latency fromJson(Json json) {
    Latency.Builder builder = Latency.builder();
    if (json.median != null) {
      builder.median(json.median);
    }
    if (json.percentile98th != null) {
      builder.percentile98th(json.percentile98th);
    }
    if (json.percentile99th != null) {
      builder.percentile99th(json.percentile99th);
    }
    if (json.percentile999th != null) {
      builder.percentile999th(json.percentile999th);
    }
    if (json.mean != null) {
      builder.mean(json.mean);
    }
    if (json.min != null) {
      builder.min(json.min);
    }
    if (json.max != null) {
      builder.max(json.max);
    }
    return builder.build();
  }

  /**
   * Creates an immutable copy of a {@link AbstractLatency} value.
   * Uses accessors to get values to initialize the new immutable instance.
   * If an instance is already immutable, it is returned as is.
   * @param instance The instance to copy
   * @return A copied immutable Latency instance
   */
  public static Latency copyOf(AbstractLatency instance) {
    if (instance instanceof Latency) {
      return (Latency) instance;
    }
    return Latency.builder()
        .from(instance)
        .build();
  }

  /**
   * Creates a builder for {@link com.lightbend.lagom.javadsl.server.status.Latency Latency}.
   * @return A new Latency builder
   */
  public static Latency.Builder builder() {
    return new Latency.Builder();
  }

  /**
   * Builds instances of type {@link com.lightbend.lagom.javadsl.server.status.Latency Latency}.
   * Initialize attributes and then invoke the {@link #build()} method to create an
   * immutable instance.
   * <p><em>{@code Builder} is not thread-safe and generally should not be stored in a field or collection,
   * but instead used immediately to create instances.</em>
   */
  @NotThreadSafe
  public static final class Builder {
    private static final long INIT_BIT_MEDIAN = 0x1L;
    private static final long INIT_BIT_PERCENTILE98TH = 0x2L;
    private static final long INIT_BIT_PERCENTILE99TH = 0x4L;
    private static final long INIT_BIT_PERCENTILE999TH = 0x8L;
    private static final long INIT_BIT_MEAN = 0x10L;
    private static final long INIT_BIT_MIN = 0x20L;
    private static final long INIT_BIT_MAX = 0x40L;
    private long initBits = 0x7f;

    private double median;
    private double percentile98th;
    private double percentile99th;
    private double percentile999th;
    private double mean;
    private long min;
    private long max;

    private Builder() {}

    /**
     * Fill a builder with attribute values from the provided {@link AbstractLatency} instance.
     * Regular attribute values will be replaced with those from the given instance.
     * Absent optional values will not replace present values.
     * @param instance The instance from which to copy values
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder from(AbstractLatency instance) {
      Preconditions.checkNotNull(instance, "instance");
      median(instance.getMedian());
      percentile98th(instance.getPercentile98th());
      percentile99th(instance.getPercentile99th());
      percentile999th(instance.getPercentile999th());
      mean(instance.getMean());
      min(instance.getMin());
      max(instance.getMax());
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractLatency#getMedian() median} attribute.
     * @param median The value for median 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder median(double median) {
      this.median = median;
      initBits &= ~INIT_BIT_MEDIAN;
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractLatency#getPercentile98th() percentile98th} attribute.
     * @param percentile98th The value for percentile98th 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder percentile98th(double percentile98th) {
      this.percentile98th = percentile98th;
      initBits &= ~INIT_BIT_PERCENTILE98TH;
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractLatency#getPercentile99th() percentile99th} attribute.
     * @param percentile99th The value for percentile99th 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder percentile99th(double percentile99th) {
      this.percentile99th = percentile99th;
      initBits &= ~INIT_BIT_PERCENTILE99TH;
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractLatency#getPercentile999th() percentile999th} attribute.
     * @param percentile999th The value for percentile999th 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder percentile999th(double percentile999th) {
      this.percentile999th = percentile999th;
      initBits &= ~INIT_BIT_PERCENTILE999TH;
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractLatency#getMean() mean} attribute.
     * @param mean The value for mean 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder mean(double mean) {
      this.mean = mean;
      initBits &= ~INIT_BIT_MEAN;
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractLatency#getMin() min} attribute.
     * @param min The value for min 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder min(long min) {
      this.min = min;
      initBits &= ~INIT_BIT_MIN;
      return this;
    }

    /**
     * Initializes the value for the {@link AbstractLatency#getMax() max} attribute.
     * @param max The value for max 
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder max(long max) {
      this.max = max;
      initBits &= ~INIT_BIT_MAX;
      return this;
    }
    /**
     * Builds a new {@link com.lightbend.lagom.javadsl.server.status.Latency Latency}.
     * @return An immutable instance of Latency
     * @throws java.lang.IllegalStateException if any required attributes are missing
     */
    public Latency build()
        throws IllegalStateException {
      checkRequiredAttributes();
      return new Latency(median, percentile98th, percentile99th, percentile999th, mean, min, max);
    }

    private boolean medianIsSet() {
      return (initBits & INIT_BIT_MEDIAN) == 0;
    }

    private boolean percentile98thIsSet() {
      return (initBits & INIT_BIT_PERCENTILE98TH) == 0;
    }

    private boolean percentile99thIsSet() {
      return (initBits & INIT_BIT_PERCENTILE99TH) == 0;
    }

    private boolean percentile999thIsSet() {
      return (initBits & INIT_BIT_PERCENTILE999TH) == 0;
    }

    private boolean meanIsSet() {
      return (initBits & INIT_BIT_MEAN) == 0;
    }

    private boolean minIsSet() {
      return (initBits & INIT_BIT_MIN) == 0;
    }

    private boolean maxIsSet() {
      return (initBits & INIT_BIT_MAX) == 0;
    }

    private void checkRequiredAttributes() throws IllegalStateException {
      if (initBits != 0) {
        throw new IllegalStateException(formatRequiredAttributesMessage());
      }
    }
    private String formatRequiredAttributesMessage() {
      List<String> attributes = Lists.newArrayList();
      if (!medianIsSet()) attributes.add("median");
      if (!percentile98thIsSet()) attributes.add("percentile98th");
      if (!percentile99thIsSet()) attributes.add("percentile99th");
      if (!percentile999thIsSet()) attributes.add("percentile999th");
      if (!meanIsSet()) attributes.add("mean");
      if (!minIsSet()) attributes.add("min");
      if (!maxIsSet()) attributes.add("max");
      return "Cannot build Latency, some of required attributes are not set " + attributes;
    }
  }
}
