/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import java.net.URI;

/**
 * @deprecated since 1.4.0 - This class became obsolete and will be removed on next release.
 */
@Deprecated
public final class CassandraContactPoint {
  private final String name;
  private final URI uri;

  private CassandraContactPoint(String name, URI uri) {
    this.name = Preconditions.checkNotNull(name, "name");
    this.uri = Preconditions.checkNotNull(uri, "uri");
  }

  /**
   * @return The value of the {@code name} attribute
   */
  public String name() {
    return name;
  }

  /**
   * @return The value of the {@code uri} attribute
   */
  public URI uri() {
    return uri;
  }

  /**
   * This instance is equal to all instances of {@code CassandraContactPoint} that have equal attribute values.
   * @return {@code true} if {@code this} is equal to {@code another} instance
   */
  @Override
  public boolean equals(Object another) {
    return this == another || another instanceof CassandraContactPoint
        && equalTo((CassandraContactPoint) another);
  }

  private boolean equalTo(CassandraContactPoint another) {
    return name.equals(another.name)
        && uri.equals(another.uri);
  }

  /**
   * Computes a hash code from attributes: {@code name}, {@code uri}.
   * @return hashCode value
   */
  @Override
  public int hashCode() {
    int h = 31;
    h = h * 17 + name.hashCode();
    h = h * 17 + uri.hashCode();
    return h;
  }

  /**
   * Prints the immutable value {@code CassandraContactPoint...} with all non-generated
   * and non-auxiliary attribute values.
   * @return A string representation of the value
   */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper("CassandraContactPoint")
        .add("name", name)
        .add("uri", uri)
        .toString();
  }

  /**
   * Construct a new immutable {@code CassandraContactPoint} instance.
   * @param name The value for the {@code name} attribute
   * @param uri The value for the {@code uri} attribute
   * @return An immutable CassandraContactPoint instance
   */
  public static CassandraContactPoint of(String name, URI uri) {
    return new CassandraContactPoint(name, uri);
  }
}
