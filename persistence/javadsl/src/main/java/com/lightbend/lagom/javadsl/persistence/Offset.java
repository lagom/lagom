/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence;

import java.util.UUID;

/**
 * An offset.
 *
 * Offsets are used for ordering of events, typically in event journals, so that consumers can keep track of what events
 * they have and haven't consumed.
 *
 * Akka persistence, which underlies Lagom's persistence APIs, uses different offset types for different persistence
 * datastores. This class provides an abstraction over them. The two types currently supported are a <code>long</code>
 * sequence number and a time based <code>UUID</code>.
 */
public abstract class Offset {

    private Offset() {}

    /**
     * Create a sequence offset.
     *
     * @param value The sequence number to create it from.
     * @return The sequence offset.
     */
    public static Offset sequence(long value) {
        return new Sequence(value);
    }

    /**
     * Create a time based UUID offset.
     *
     * @param uuid The timebased UUID to create it from.
     * @return The time based UUID.
     */
    public static Offset timeBasedUUID(UUID uuid) {
        return new TimeBasedUUID(uuid);
    }

    /**
     * No offset.
     */
    public static final Offset NONE = new NoOffset();

    /**
     * A sequence number offset, backed by a long.
     */
    public static final class Sequence extends Offset implements Comparable<Sequence> {
        private final long value;

        public Sequence(long value) {
            this.value = value;
        }

        public long value() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Sequence sequence1 = (Sequence) o;

            return value == sequence1.value;

        }

        @Override
        public int hashCode() {
            return Long.hashCode(value);
        }

        @Override
        public String toString() {
            return Long.toString(value);
        }

        @Override
        public int compareTo(Sequence o) {
            return Long.compare(value, o.value);
        }
    }

    /**
     * A time-based UUID offset, backed by a UUID.
     */
    public static final class TimeBasedUUID extends Offset implements Comparable<TimeBasedUUID> {
        private final UUID value;

        public TimeBasedUUID(UUID value) {
            if (value == null || value.version() != 1) {
                throw new IllegalArgumentException("UUID " + value + " is not a time-based UUID");
            }
            this.value = value;
        }

        public UUID value() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TimeBasedUUID that = (TimeBasedUUID) o;

            return value.equals(that.value);

        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value.toString();
        }

        @Override
        public int compareTo(TimeBasedUUID o) {
            return value.compareTo(o.value);
        }
    }

    public static final class NoOffset extends Offset {
        private NoOffset() {}

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof NoOffset;
        }

        @Override
        public String toString() {
            return "NoOffset";
        }
    }
}
