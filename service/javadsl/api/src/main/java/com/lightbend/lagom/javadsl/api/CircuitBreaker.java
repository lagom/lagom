/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api;

/**
 * Circuit breaker descriptor. Describes how circuit breaking should be applied
 * to a particular {@link ServiceCall}.
 * A circuit breaker is used to provide stability and prevent cascading
 * failures in distributed systems. An example would be to begin to fail-fast
 * after some number of failures due to a dependency on a hanging call.
 */
public abstract class CircuitBreaker {

    private CircuitBreaker() {}

    /**
     * Do not use a Circuit breaker for this service call.
     */
    public static CircuitBreaker none() {
        return NamedCircuitBreaker.NONE;
    }

    /**
     * Use a circuit breaker per node, if supported.
     *
     * Service locators that support per node circuit breaking will typically also remove the node from their routing
     * pool while the circuit breaker is open, and bring it in for one call when it's half open.
     *
     * If not supported by the service locator, a circuit breaker per service may be used.
     */
    public static CircuitBreaker perNode() {
        return NamedCircuitBreaker.PER_NODE;
    }

    /**
     * Use a circuit breaker per service.
     */
    public static CircuitBreaker perService() {
        return NamedCircuitBreaker.PER_SERVICE;
    }

    /**
     * Use a circuit breaker identified by the given circuit breaker ID.
     *
     * @param id The ID of the circuit breaker.
     * @return The circuit breaker descriptor.
     */
    public static CircuitBreaker identifiedBy(String id) {
        return new CircuitBreakerId(id);
    }

    /**
     * A named circuit breaker.
     */
    public static final class NamedCircuitBreaker extends CircuitBreaker {

        // These are defined here, and not on CircuitBreaker, due to classloading deadlocks inherent with super classes
        // referencing sub classes in static initializers.
        public static final CircuitBreaker NONE = new NamedCircuitBreaker("NONE");
        public static final CircuitBreaker PER_NODE = new NamedCircuitBreaker("PER_NODE");
        public static final CircuitBreaker PER_SERVICE = new NamedCircuitBreaker("PER_SERVICE");

        private final String name;

        private NamedCircuitBreaker(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            NamedCircuitBreaker that = (NamedCircuitBreaker) o;

            return name.equals(that.name);

        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return "NamedCircuitBreaker{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }

    public static final class CircuitBreakerId extends CircuitBreaker {
        private final String id;

        private CircuitBreakerId(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CircuitBreakerId that = (CircuitBreakerId) o;

            return id.equals(that.id);

        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return "CircuitBreakerId{" +
                    "id='" + id + '\'' +
                    '}';
        }
    }
}
