/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.broker;

import com.lightbend.lagom.javadsl.persistence.Offset;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unused")
public interface TopicProducerCommand<T> {
  final class EmitMultipleAndCommit<Message> implements TopicProducerCommand<Message> {
    private final List<Message> messages;
    private final Offset offset;

    public EmitMultipleAndCommit(List<Message> messages, Offset offset) {
      this.messages = messages;
      this.offset = offset;
    }

    public List<Message> messages() {
      return Collections.unmodifiableList(this.messages);
    }

    public Offset offset() {
      return this.offset;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final EmitMultipleAndCommit<?> that = (EmitMultipleAndCommit<?>) o;

      return messages.equals(that.messages) && offset.equals(that.offset);
    }

    @Override
    public int hashCode() {
      return Objects.hash(messages, offset);
    }
  }

  final class EmitAndCommit<Message> implements TopicProducerCommand<Message> {
    private final Message message;
    private final Offset offset;

    public EmitAndCommit(Message message, Offset offset) {
      this.message = message;
      this.offset = offset;
    }

    public Message message() {
      return this.message;
    }

    public Offset offset() {
      return this.offset;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final EmitAndCommit<?> that = (EmitAndCommit<?>) o;

      return message.equals(that.message) && offset.equals(that.offset);
    }

    @Override
    public int hashCode() {
      return Objects.hash(message, offset);
    }
  }

  final class Commit<Message> implements TopicProducerCommand<Message> {
    private final Offset offset;

    public Commit(Offset offset) {
      this.offset = offset;
    }

    public Offset offset() {
      return this.offset;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final Commit<?> commit = (Commit<?>) o;
      return offset.equals(commit.offset);
    }

    @Override
    public int hashCode() {
      return Objects.hash(offset);
    }
  }
}
