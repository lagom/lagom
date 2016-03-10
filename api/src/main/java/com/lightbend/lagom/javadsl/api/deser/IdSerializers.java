/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.deser;

import akka.japi.function.*;
import com.lightbend.lagom.internal.api.InternalIdSerializers;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.paging.Page;
import org.pcollections.TreePVector;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Id Serializers.
 */
public final class IdSerializers {

    private IdSerializers() {}

    /**
     * Create an IdSerializer from the given serialize and deserialize functions.
     *
     * This is useful for situations where the ID is expressed as a single parameter, and the name of that parameter
     * doesn't matter.
     *
     * @param name The name of the serializer. This is used for debugging purposes, so that the toString method returns
     *             something meaningful.
     * @param deserialize The deserialize function.
     * @param serialize The serialize function.
     * @param <Id> The type of the Id that this serializer is for.
     * @return The serializer.
     * @see IdSerializer
     */
    public static <Id> IdSerializer<Id> createSinglePathParam(String name, Function<String, Id> deserialize,
            Function<Id, String> serialize) {
        return new NamedIdSerializer<Id>(name) {
            @Override
            public RawId serialize(Id id) {
                return RawId.fromPathValues(TreePVector.singleton(serialize.apply(id)));
            }

            @Override
            public Id deserialize(RawId rawId) {
                if (rawId.pathParams().isEmpty()) {
                    throw new IllegalArgumentException(name + " can't parse empty path");
                } else {
                    return deserialize.apply(rawId.pathParams().get(0).value());
                }
            }

            @Override
            public Optional<Integer> numPathParamsHint() {
                return Optional.of(1);
            }
        };
    }

    public static <Id, Arg1> IdSerializer<Id> create(String name, Function<Arg1, Id> deserialize, Function<Id, Arg1> serialize) {
        return InternalIdSerializers.fromFunction(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2> IdSerializer<Id> create(String name, BiFunction<Arg1, Arg2, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromBiFunction(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3> IdSerializer<Id> create(String name,
            Function3<Arg1, Arg2, Arg3, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction3(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4> IdSerializer<Id> create(String name,
            Function4<Arg1, Arg2, Arg3, Arg4, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction4(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5> IdSerializer<Id> create(String name,
            Function5<Arg1, Arg2, Arg3, Arg4, Arg5, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction5(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6> IdSerializer<Id> create(String name,
            Function6<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction6(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7> IdSerializer<Id> create(String name,
            Function7<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction7(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8> IdSerializer<Id> create(String name,
            Function8<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction8(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9> IdSerializer<Id> create(String name,
            Function9<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction9(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10> IdSerializer<Id> create(String name,
            Function10<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction10(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11> IdSerializer<Id> create(String name,
            Function11<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction11(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12> IdSerializer<Id> create(String name,
            Function12<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction12(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13> IdSerializer<Id> create(String name,
            Function13<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction13(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14> IdSerializer<Id> create(String name,
            Function14<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction14(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15> IdSerializer<Id> create(String name,
            Function15<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction15(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16> IdSerializer<Id> create(String name,
            Function16<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction16(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17> IdSerializer<Id> create(String name,
            Function17<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction17(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18> IdSerializer<Id> create(String name,
            Function18<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction18(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Arg19> IdSerializer<Id> create(String name,
            Function19<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Arg19, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction19(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Arg19, Arg20> IdSerializer<Id> create(String name,
            Function20<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Arg19, Arg20, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction20(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Arg19, Arg20, Arg21> IdSerializer<Id> create(String name,
            Function21<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Arg19, Arg20, Arg21, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction21(name, deserialize, serialize);
    }

    public static <Id, Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Arg19, Arg20, Arg21, Arg22> IdSerializer<Id> create(String name,
            Function22<Arg1, Arg2, Arg3, Arg4, Arg5, Arg6, Arg7, Arg8, Arg9, Arg10, Arg11, Arg12, Arg13, Arg14, Arg15, Arg16, Arg17, Arg18, Arg19, Arg20, Arg21, Arg22, Id> deserialize,
            Function<Id, List<Object>> serialize) {
        return InternalIdSerializers.fromFunction22(name, deserialize, serialize);
    }

    /**
     * A String id serializer.
     */
    public static final IdSerializer<String> STRING = createSinglePathParam("String", Function.<String>identity(),
            Function.<String>identity());

    /**
     * A Long id serializer.
     */
    public static final IdSerializer<Long> LONG = createSinglePathParam("Long",  Long::parseLong, l -> l.toString());

    /**
     * An Integer id serializer.
     */
    public static final IdSerializer<Integer> INTEGER = createSinglePathParam("Integer",  Integer::parseInt, i -> i.toString());

    /**
     * A page serializer.
     */
    // IdSerializers.<Page> is needed here due to a type inference bug in JDK 1.8.0_u31
    public static final IdSerializer<Page> PAGE = new NamedIdSerializer<Page>("Page") {
        @Override
        public RawId serialize(Page page) {
            return RawId.EMPTY.withQueryParam("pageNo", page.pageNo().map(i -> i.toString()))
                    .withQueryParam("pageSize", page.pageSize().map(i -> i.toString()));
        }

        @Override
        public Page deserialize(RawId rawId) {
            Optional<Integer> pageNo = rawId.queryParam("pageNo").map(Integer::parseInt);
            Optional<Integer> pageSize = rawId.queryParam("pageSize").map(Integer::parseInt);
            return new Page(pageNo, pageSize);
        }

        @Override
        public Optional<Integer> numPathParamsHint() {
            return Optional.of(0);
        }
    };

    /**
     * A unit id serializer.
     */
    public static final IdSerializer<NotUsed> NOT_USED = new NamedIdSerializer<NotUsed>("NotUsed") {
        @Override
        public Optional<Integer> numPathParamsHint() {
            return Optional.of(0);
        }

        @Override
        public RawId serialize(NotUsed o) {
            return RawId.EMPTY;
        }

        @Override
        public NotUsed deserialize(RawId rawId) {
            return NotUsed.getInstance();
        }
    };

    private static abstract class NamedIdSerializer<Id> implements IdSerializer<Id> {
        private final String name;

        public NamedIdSerializer(String name) {
            this.name = name;
        }

        // Force us to implement it
        @Override
        public abstract Optional<Integer> numPathParamsHint();

        @Override
        public String toString() {
            return "IdSerializer(" + name + ")";
        }
    }
}
