/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.broker;

/**
 * A topic can be used to publish/subscribe messages to/from a message broker.
 * 
 * @param <Message> The message type.
 *
 * @note This class is not meant to be extended by client code.
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
   * Obtain a publisher to this topic.
   * @return A Publisher to this topic.
   */
  Publisher<Message> publisher();

  /**
   * A topic identifier.
   */
  public static final class TopicId {

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
