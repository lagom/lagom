/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api;

import java.util.Optional;

import com.lightbend.lagom.internal.javadsl.api.MethodRefMessageSerializer;

import akka.japi.function.*;

import com.lightbend.lagom.internal.javadsl.api.MethodRefServiceCallHolder;
import com.lightbend.lagom.internal.javadsl.api.MethodRefTopicHolder;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId;
import com.lightbend.lagom.javadsl.api.transport.Method;
import org.pcollections.HashTreePMap;

/**
 * A Lagom service descriptor.
 *
 * This interface implements a DSL for describing a service. It is inherently constrained in its use.
 *
 * A service can describe itself by defining an interface that extends this interface, and provides a default
 * implementation for the {@link #descriptor()} method.
 */
public interface Service {

    /**
     * Describe this service.
     *
     * The intended mechanism for implementing this is to provide it as a default implementation on an interface.
     */
    Descriptor descriptor();

    /**
     * Create a descriptor for a service with the given name.
     *
     * @param name The name of the service.
     * @return The descriptor.
     */
    static Descriptor named(String name) {
        return new Descriptor(name);
    }

    /**
     * Create a REST service call descriptor, identified by the given HTTP method and path pattern.
     *
     * @param method The HTTP method.
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A REST service call descriptor.
     */
    static <Request, Response> Descriptor.Call<Request, Response> restCall(Method method,
            String pathPattern, Creator<ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.RestCallId(method, pathPattern), methodRef);
    }

    /**
     * Create a REST service call descriptor, identified by the given HTTP method and path pattern.
     *
     * @param method The HTTP method.
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A REST service call descriptor.
     */
    static <Request, Response, A> Descriptor.Call<Request, Response> restCall(Method method,
            String pathPattern, Function<A, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.RestCallId(method, pathPattern), methodRef);
    }

    /**
     * Create a REST service call descriptor, identified by the given HTTP method and path pattern.
     *
     * @param method The HTTP method.
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A REST service call descriptor.
     */
    static <Request, Response, A, B> Descriptor.Call<Request, Response> restCall(Method method,
            String pathPattern, Function2<A, B, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.RestCallId(method, pathPattern), methodRef);
    }

    /**
     * Create a REST service call descriptor, identified by the given HTTP method and path pattern.
     *
     * @param method The HTTP method.
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A REST service call descriptor.
     */
    static <Request, Response, A, B, C> Descriptor.Call<Request, Response> restCall(Method method,
            String pathPattern, Function3<A, B, C, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.RestCallId(method, pathPattern), methodRef);
    }

    /**
     * Create a REST service call descriptor, identified by the given HTTP method and path pattern.
     *
     * @param method The HTTP method.
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A REST service call descriptor.
     */
    static <Request, Response, A, B, C, D> Descriptor.Call<Request, Response> restCall(Method method,
            String pathPattern, Function4<A, B, C, D, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.RestCallId(method, pathPattern), methodRef);
    }

    /**
     * Create a REST service call descriptor, identified by the given HTTP method and path pattern.
     *
     * @param method The HTTP method.
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A REST service call descriptor.
     */
    static <Request, Response, A, B, C, D, E> Descriptor.Call<Request, Response> restCall(Method method,
            String pathPattern, Function5<A, B, C, D, E, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.RestCallId(method, pathPattern), methodRef);
    }

    /**
     * Create a REST service call descriptor, identified by the given HTTP method and path pattern.
     *
     * @param method The HTTP method.
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A REST service call descriptor.
     */
    static <Request, Response, A, B, C, D, E, F> Descriptor.Call<Request, Response> restCall(Method method,
            String pathPattern, Function6<A, B, C, D, E, F, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.RestCallId(method, pathPattern), methodRef);
    }

    /**
     * Create a REST service call descriptor, identified by the given HTTP method and path pattern.
     *
     * @param method The HTTP method.
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A REST service call descriptor.
     */
    static <Request, Response, A, B, C, D, E, F, G> Descriptor.Call<Request, Response> restCall(Method method,
            String pathPattern, Function7<A, B, C, D, E, F, G, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.RestCallId(method, pathPattern), methodRef);
    }

    /**
     * Create a REST service call descriptor, identified by the given HTTP method and path pattern.
     *
     * @param method The HTTP method.
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A REST service call descriptor.
     */
    static <Request, Response, A, B, C, D, E, F, G, H> Descriptor.Call<Request, Response> restCall(Method method,
            String pathPattern, Function8<A, B, C, D, E, F, G, H, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.RestCallId(method, pathPattern), methodRef);
    }

    /**
     * Create a REST service call descriptor, identified by the given HTTP method and path pattern.
     *
     * @param method The HTTP method.
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A REST service call descriptor.
     */
    static <Request, Response, A, B, C, D, E, F, G, H, I> Descriptor.Call<Request, Response> restCall(Method method,
            String pathPattern, Function9<A, B, C, D, E, F, G, H, I, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.RestCallId(method, pathPattern), methodRef);
    }

    /**
     * Create a REST service call descriptor, identified by the given HTTP method and path pattern.
     *
     * @param method The HTTP method.
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A REST service call descriptor.
     */
    static <Request, Response, A, B, C, D, E, F, G, H, I, J> Descriptor.Call<Request, Response> restCall(Method method,
            String pathPattern, Function10<A, B, C, D, E, F, G, H, I, J, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.RestCallId(method, pathPattern), methodRef);
    }

    /**
     * Create a REST service call descriptor, identified by the given HTTP method and path pattern.
     *
     * @param method The HTTP method.
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A REST service call descriptor.
     */
    static <Request, Response, A, B, C, D, E, F, G, H, I, J, K> Descriptor.Call<Request, Response> restCall(Method method,
            String pathPattern, Function11<A, B, C, D, E, F, G, H, I, J, K, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.RestCallId(method, pathPattern), methodRef);
    }

    /**
     * Create a REST service call descriptor, identified by the given HTTP method and path pattern.
     *
     * @param method The HTTP method.
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A REST service call descriptor.
     */
    static <Request, Response> Descriptor.Call<Request, Response> restCall(Method method,
            String pathPattern, java.lang.reflect.Method methodRef) {
        return call(new Descriptor.RestCallId(method, pathPattern), methodRef);
    }

    /**
     * Create a service call descriptor, identified by the given path pattern.
     *
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A path identified service call descriptor.
     */
    static <Request, Response> Descriptor.Call<Request, Response> pathCall(String pathPattern,
            Creator<ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.PathCallId(pathPattern), methodRef);
    }

    /**
     * Create a service call descriptor, identified by the given path pattern.
     *
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A path identified service call descriptor.
     */
    static <Request, Response, A> Descriptor.Call<Request, Response> pathCall(String pathPattern,
            Function<A, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.PathCallId(pathPattern), methodRef);
    }

    /**
     * Create a service call descriptor, identified by the given path pattern.
     *
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A path identified service call descriptor.
     */
    static <Request, Response, A, B> Descriptor.Call<Request, Response> pathCall(String pathPattern,
            Function2<A, B, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.PathCallId(pathPattern), methodRef);
    }

    /**
     * Create a service call descriptor, identified by the given path pattern.
     *
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A path identified service call descriptor.
     */
    static <Request, Response, A, B, C> Descriptor.Call<Request, Response> pathCall(String pathPattern,
            Function3<A, B, C, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.PathCallId(pathPattern), methodRef);
    }

    /**
     * Create a service call descriptor, identified by the given path pattern.
     *
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A path identified service call descriptor.
     */
    static <Request, Response, A, B, C, D> Descriptor.Call<Request, Response> pathCall(String pathPattern,
            Function4<A, B, C, D, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.PathCallId(pathPattern), methodRef);
    }

    /**
     * Create a service call descriptor, identified by the given path pattern.
     *
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A path identified service call descriptor.
     */
    static <Request, Response, A, B, C, D, E> Descriptor.Call<Request, Response> pathCall(String pathPattern,
            Function5<A, B, C, D, E, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.PathCallId(pathPattern), methodRef);
    }

    /**
     * Create a service call descriptor, identified by the given path pattern.
     *
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A path identified service call descriptor.
     */
    static <Request, Response, A, B, C, D, E, F> Descriptor.Call<Request, Response> pathCall(String pathPattern,
            Function6<A, B, C, D, E, F, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.PathCallId(pathPattern), methodRef);
    }

    /**
     * Create a service call descriptor, identified by the given path pattern.
     *
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A path identified service call descriptor.
     */
    static <Request, Response, A, B, C, D, E, F, G> Descriptor.Call<Request, Response> pathCall(String pathPattern,
            Function7<A, B, C, D, E, F, G, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.PathCallId(pathPattern), methodRef);
    }

    /**
     * Create a service call descriptor, identified by the given path pattern.
     *
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A path identified service call descriptor.
     */
    static <Request, Response, A, B, C, D, E, F, G, H> Descriptor.Call<Request, Response> pathCall(String pathPattern,
            Function8<A, B, C, D, E, F, G, H, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.PathCallId(pathPattern), methodRef);
    }

    /**
     * Create a service call descriptor, identified by the given path pattern.
     *
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A path identified service call descriptor.
     */
    static <Request, Response, A, B, C, D, E, F, G, H, I> Descriptor.Call<Request, Response> pathCall(String pathPattern,
            Function9<A, B, C, D, E, F, G, H, I, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.PathCallId(pathPattern), methodRef);
    }

    /**
     * Create a service call descriptor, identified by the given path pattern.
     *
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A path identified service call descriptor.
     */
    static <Request, Response, A, B, C, D, E, F, G, H, I, J> Descriptor.Call<Request, Response> pathCall(String pathPattern,
            Function10<A, B, C, D, E, F, G, H, I, J, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.PathCallId(pathPattern), methodRef);
    }

    /**
     * Create a service call descriptor, identified by the given path pattern.
     *
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A path identified service call descriptor.
     */
    static <Request, Response, A, B, C, D, E, F, G, H, I, J, K> Descriptor.Call<Request, Response> pathCall(String pathPattern,
            Function11<A, B, C, D, E, F, G, H, I, J, K, ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.PathCallId(pathPattern), methodRef);
    }

    /**
     * Create a service call descriptor, identified by the given path pattern.
     *
     * @param pathPattern The path pattern.
     * @param methodRef A method reference to the service call.
     * @return A path identified service call descriptor.
     */
    static <Request, Response> Descriptor.Call<Request, Response> pathCall(String pathPattern,
            java.lang.reflect.Method methodRef) {
        return call(new Descriptor.PathCallId(pathPattern), methodRef);
    }


    /**
     * Create a service call descriptor, identified by the given name.
     *
     * @param name The name of the service call.
     * @param methodRef The service call.
     * @return A name identified service call descriptor.
     */
    static <Request, Response> Descriptor.Call<Request, Response> namedCall(String name,
            Creator<ServiceCall<Request, Response>> methodRef) {
        return call(new Descriptor.NamedCallId(name), methodRef);
    }

    /**
     * Create a service call descriptor, identified by the given name.
     *
     * @param name The name of the service call.
     * @param methodRef The service call.
     * @return A name identified service call descriptor.
     */
    static <Request, Response> Descriptor.Call<Request, Response> namedCall(String name,
            java.lang.reflect.Method methodRef) {
        return call(new Descriptor.NamedCallId(name), methodRef);
    }

    /**
     * Create a service call descriptor.
     *
     * The identifier for this descriptor will be automatically selected by Lagom.
     *
     * @param methodRef The service call.
     * @return A name identified service call descriptor.
     */
    static <Request, Response> Descriptor.Call<Request, Response> call(
            Creator<ServiceCall<Request, Response>> methodRef) {
        return namedCall("__unresolved__", methodRef);
    }

    /**
     * Create a service call descriptor.
     *
     * The identifier for this descriptor will be automatically selected by Lagom.
     *
     * @param methodRef The service call.
     * @return A name identified service call descriptor.
     */
    static <Request, Response> Descriptor.Call<Request, Response> call(
            java.lang.reflect.Method methodRef) {
        return namedCall(methodRef.getName(), methodRef);
    }

    /**
     * Create a service call descriptor.
     *
     * @param callId The service call identifier.
     * @param methodRef The service call.
     * @return A service call descriptor.
     */
    static <Request, Response> Descriptor.Call<Request, Response> call(Descriptor.CallId callId,
            Object methodRef) {
        return new Descriptor.Call<>(callId, new MethodRefServiceCallHolder(methodRef),
                new MethodRefMessageSerializer<>(), new MethodRefMessageSerializer<>(),
                Optional.empty(), Optional.empty());
    }

    /**
     * Create a topic call descriptor, identified by the given topic id.
     *
     * @param topicId The topic identifier.
     * @param methodRef The topic  call.
     * @return A topic call descriptor.
     */
    static <Message> Descriptor.TopicCall<Message> topic(String topicId, java.lang.reflect.Method methodRef) {
      return topic(topicId, (Object) methodRef);
    }

    /**
     * Create a topic call descriptor, identified by the given topic id.
     *
     * @param topicId The topic identifier.
     * @param methodRef The topic  call.
     * @return A topic call descriptor.
     */
    static <Message> Descriptor.TopicCall<Message> topic(String topicId, Creator<Topic<Message>> methodRef) {
      return topic(topicId, (Object) methodRef);
    }

    /**
     * Create a topic call descriptor, identified by the given topic id.
     *
     * @param topicId The topic identifier.
     * @param methodRef The topic  call.
     * @return A topic call descriptor.
     */
    static <Message> Descriptor.TopicCall<Message> topic(String topicId, Object methodRef) {
      return new Descriptor.TopicCall<>(TopicId.of(topicId), new MethodRefTopicHolder(methodRef),
               new MethodRefMessageSerializer<>(), new Descriptor.Properties<>(HashTreePMap.empty()));
    }

}
