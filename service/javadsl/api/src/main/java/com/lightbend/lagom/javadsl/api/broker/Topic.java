/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.broker;

/**
 * A topic can be used to publish/subscribe messages to/from a message broker.
 * 
 * @param <Message> The message type.
 *
 * Note: This class is not meant to be extended by client code.
 */
public interface Topic<Message> {

  /**
   * The topic identifier.
   * @return The topic identifier.
   */
  TopicId topicId();

  /**
   * Obtain a subscriber to this topic.
   * @return A Subscriber to this topic.
   */
  Subscriber<Message> subscribe();

  /**
   * A topic identifier.
   */
  final class TopicId {

    private final String value;

    private TopicId(String value) {
      this.value = value;
    }

    /**
     * Factory for creating topic's identifiers.
     */
    public static TopicId of(String topicId) {
      return new TopicId(topicId);
    }

    /**
     * The topic identifier held by this instance.
     * @return The topic identifier.
     */
    public String value() {
      return value;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((value == null) ? 0 : value.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      TopicId other = (TopicId) obj;
      if (value == null) {
        if (other.value != null)
          return false;
      } else if (!value.equals(other.value))
        return false;
      return true;
    }

    @Override
    public String toString() {
      return "{ topicId = " + value + " }";
    }
  }

}
