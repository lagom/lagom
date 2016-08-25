/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.broker.modules;

import org.pcollections.PMap;

/**
 * Holds a map of properties. The stored properties are specific to each message
 * broker implementation.
 *
 * @note This class is useful only to create new message broker module implementations, 
 * and should not leak into the user api.
 */
final public class Properties {
  public static final class Property<T> {}

  private final PMap<Property<Object>, Object> properties;

  public Properties(PMap<Property<Object>, Object> properties) {
    this.properties = properties;
  }

  /**
   * Returns the value associated with the passed property, or null if 
   * the no matching property is found.
   *
   * @param property The property to look up.
   * @throws ClassCastException if the value stored in the properties map cannot be 
   *         cast to the property's expected type.  
   * @return The value associated with the passed property, or null if no match exist.
   */
  @SuppressWarnings("unchecked")
  public <T> T getValueOf(Property<T> property) {
    return (T) properties.get(property);
  }
}
