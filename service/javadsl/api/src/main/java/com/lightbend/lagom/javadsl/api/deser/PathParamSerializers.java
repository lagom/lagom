/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.deser;

import com.lightbend.lagom.internal.javadsl.api.UnresolvedCollectionPathParamSerializer;
import com.lightbend.lagom.internal.javadsl.api.UnresolvedListPathParamSerializer;
import com.lightbend.lagom.internal.javadsl.api.UnresolvedOptionalPathParamSerializer;
import com.lightbend.lagom.internal.javadsl.api.UnresolvedSetPathParamSerializer;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Id Serializers.
 */
public final class PathParamSerializers {

    private PathParamSerializers() {
    }

    /**
     * Create a PathParamSerializer for required parameters.
     */
    public static <Param> PathParamSerializer<Param> required(String name, Function<String, Param> deserialize,
                                                              Function<Param, String> serialize) {
        return new NamedPathParamSerializer<Param>(name) {
            @Override
            public PSequence<String> serialize(Param parameter) {
                return TreePVector.singleton(serialize.apply(parameter));
            }

            @Override
            public Param deserialize(PSequence<String> parameters) {
                if (parameters.isEmpty()) {
                    throw new IllegalArgumentException(name + " parameter is required");
                } else {
                    return deserialize.apply(parameters.get(0));
                }
            }
        };
    }

    /**
     * Create a PathParamSerializer for optional parameters.
     */
    public static <Param> PathParamSerializer<Optional<Param>> optional(String name,
                                                                        Function<String, Param> deserialize,
                                                                        Function<Param, String> serialize) {

        return new NamedPathParamSerializer<Optional<Param>>("Optional(" + name + ")") {
            @Override
            public PSequence<String> serialize(Optional<Param> parameter) {
                return parameter.map(p -> TreePVector.singleton(serialize.apply(p))).orElse(TreePVector.empty());
            }

            @Override
            public Optional<Param> deserialize(PSequence<String> parameters) {
                if (parameters.isEmpty()) {
                    return Optional.empty();
                } else {
                    return Optional.of(deserialize.apply(parameters.get(0)));
                }
            }
        };
    }


    private static <Param> Stream<String> serializeCollection(Collection<Param> parameter,
                                                              Function<Param, PSequence<String>> serialize) {
        return parameter
            .stream()
            .flatMap(s -> serialize.apply(s).stream());
    }


    private static <Param> Stream<Param> deserializeCollection(PSequence<String> parameters, Function<PSequence<String>, Param> deserialize) {
        return
            parameters
            .stream()
            .map(s -> deserialize.apply(TreePVector.singleton(s)));
    }


    /**
     * Create a PathParamSerializer for List parameters.
     */
    public static <Param> PathParamSerializer<List<Param>> list(String name,
                                                                Function<PSequence<String>, Param> deserialize,
                                                                Function<Param, PSequence<String>> serialize) {

        return new NamedPathParamSerializer<List<Param>>("List(" + name + ")") {
            @Override
            public PSequence<String> serialize(List<Param> parameter) {
                List<String> serializedParams =
                    serializeCollection(parameter, serialize)
                        .collect(Collectors.toList());
                return TreePVector.from(serializedParams);
            }

            @Override
            public List<Param> deserialize(PSequence<String> parameters) {
                return deserializeCollection(parameters, deserialize).collect(Collectors.toList());
            }
        };
    }


    /**
     * Create a PathParamSerializer for Set parameters.
     */
    public static <Param> PathParamSerializer<Set<Param>> set(String name,
                                                              Function<PSequence<String>, Param> deserialize,
                                                              Function<Param, PSequence<String>> serialize) {

        return new NamedPathParamSerializer<Set<Param>>("Set(" + name + ")") {
            @Override
            public PSequence<String> serialize(Set<Param> parameter) {
                Set<String> serializedParams =
                    serializeCollection(parameter, serialize)
                        .collect(Collectors.toSet());
                return TreePVector.from(serializedParams);
            }

            @Override
            public Set<Param> deserialize(PSequence<String> parameters) {
                return deserializeCollection(parameters, deserialize).collect(Collectors.toSet());
            }
        };
    }

    /**
     * Create a PathParamSerializer for Collection parameters.
     */
    public static <Param> PathParamSerializer<Collection<Param>> collection(String name,
                                                                            Function<PSequence<String>, Param> deserialize,
                                                                            Function<Param, PSequence<String>> serialize) {

        return new NamedPathParamSerializer<Collection<Param>>("Collection(" + name + ")") {
            @Override
            public PSequence<String> serialize(Collection<Param> parameter) {
                Collection<String> serializedParams =
                    serializeCollection(parameter, serialize)
                        .collect(Collectors.toList());
                return TreePVector.from(serializedParams);
            }

            @Override
            public Collection<Param> deserialize(PSequence<String> parameters) {
                return deserializeCollection(parameters, deserialize).collect(Collectors.toList());
            }
        };
    }

    /**
     * A String path param serializer.
     */
    public static final PathParamSerializer<String> STRING = required("String", Function.identity(),
        Function.identity());

    /**
     * A Long path param serializer.
     */
    public static final PathParamSerializer<Long> LONG = required("Long", Long::parseLong, l -> l.toString());

    /**
     * An Integer path param serializer.
     */
    public static final PathParamSerializer<Integer> INTEGER = required("Integer", Integer::parseInt, i -> i.toString());

    /**
     * An Double path param serializer.
     */
    public static final PathParamSerializer<Double> DOUBLE = required("Double", Double::parseDouble, d -> d.toString());

    /**
     * A Boolean path param serializer.
     */
    public static final PathParamSerializer<Boolean> BOOLEAN = required("Boolean", Boolean::parseBoolean,
        b -> b.toString());

    /**
     * A UUID path param serializer.
     */
    public static final PathParamSerializer<UUID> UUID = required("UUID", java.util.UUID::fromString,
        u -> u.toString());

    /**
     * A generic (unresolved) Optional serializer.
     */
    public static final PathParamSerializer<Optional<Object>> OPTIONAL = new UnresolvedOptionalPathParamSerializer<>();


    /**
     * A generic (unresolved) List serializer.
     */
    public static final PathParamSerializer<java.util.List<Object>> LIST = new UnresolvedListPathParamSerializer<>();


    /**
     * A generic (unresolved) Set serializer.
     */
    public static final PathParamSerializer<java.util.Set<Object>> SET = new UnresolvedSetPathParamSerializer<>();


    /**
     * A generic (unresolved) Collection serializer.
     */
    public static final PathParamSerializer<java.util.Collection<Object>> COLLECTION = new UnresolvedCollectionPathParamSerializer<>();


    private static abstract class NamedPathParamSerializer<Param> implements PathParamSerializer<Param> {
        private final String name;

        public NamedPathParamSerializer(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "PathParamSerializer(" + name + ")";
        }
    }
}
