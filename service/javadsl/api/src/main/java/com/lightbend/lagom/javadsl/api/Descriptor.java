/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api;

import java.util.Optional;

import akka.NotUsed;
import akka.util.ByteString;

import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId;
import com.lightbend.lagom.javadsl.api.deser.*;
import com.lightbend.lagom.javadsl.api.security.UserAgentHeaderFilter;
import com.lightbend.lagom.javadsl.api.transport.HeaderFilter;
import com.lightbend.lagom.javadsl.api.transport.Method;
import org.pcollections.*;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * Describes a service.
 *
 * A descriptor is a set of calls descriptors that the service provides, coupled with metadata about how the
 * service and its calls are to be served.  Metadata may include versioning and migrations, SLA's, sharding
 * hints, circuit breaker strategies etc.
 */
public final class Descriptor {

    /**
     * Holds the service call itself.
     *
     * The implementations of this are intentionally opaque, as the mechanics of how the service call implementation
     * gets passed around is internal to Lagom.
     */
    public interface ServiceCallHolder {
    }

    /**
     * Holds the topic implementation.
     *
     * The implementations of this are intentionally opaque, as the mechanics of how the service call implementation
     * gets passed around is internal to Lagom.
     */
    public interface TopicHolder {
    }

    /**
     * Describes a service call.
     */
    public static final class Call<Request, Response> {
        public static final Call<NotUsed, NotUsed> NONE = new Call<>(new NamedCallId("none"), new ServiceCallHolder() {},
                MessageSerializers.NOT_USED, MessageSerializers.NOT_USED, Optional.empty(), Optional.empty());

        private final CallId callId;
        private final ServiceCallHolder serviceCallHolder;
        private final MessageSerializer<Request, ?> requestSerializer;
        private final MessageSerializer<Response, ?> responseSerializer;
        private final Optional<CircuitBreaker> circuitBreaker;
        private final Optional<Boolean> autoAcl;


        Call(CallId callId, ServiceCallHolder serviceCallHolder,
             MessageSerializer<Request, ?> requestSerializer,
             MessageSerializer<Response, ?> responseSerializer, Optional<CircuitBreaker> circuitBreaker,
             Optional<Boolean> autoAcl) {

            this.callId = callId;
            this.serviceCallHolder = serviceCallHolder;
            this.requestSerializer = requestSerializer;
            this.responseSerializer = responseSerializer;
            this.circuitBreaker = circuitBreaker;
            this.autoAcl = autoAcl;
        }

        /**
         * Get the id for the call.
         *
         * @return The id.
         */
        public CallId callId() {
            return callId;
        }

        /**
         * A holder for the service call.
         *
         * This holds a reference to the service call, in an implementation specific way.
         *
         * @return The service call holder.
         */
        public ServiceCallHolder serviceCallHolder() {
            return serviceCallHolder;
        }

        /**
         * Get the request message serializer.
         *
         * @return The request serializer.
         */
        public MessageSerializer<Request, ?> requestSerializer() {
            return requestSerializer;
        }

        /**
         * Get the response message serializer.
         *
         * @return The response serializer.
         */
        public MessageSerializer<Response, ?> responseSerializer() {
            return responseSerializer;
        }

        /**
         * Get the circuit breaker.
         *
         * @return The circuit breaker, if configured.
         */
        public Optional<CircuitBreaker> circuitBreaker() {
          return circuitBreaker;
        }
    
        /**
         * Whether this service call should automatically define an ACL for the router to route external calls to it.
         *
         * @return Some value if this service call explicitly decides that it should have an auto ACL defined for it,
         *         otherwise empty.
         */
        public Optional<Boolean> autoAcl() {
            return autoAcl;
        }

        /**
         * Return a copy of this call descriptor with the given service call ID configured.
         *
         * @param callId
         *          The call id.
         * @return A copy of this call descriptor.
         */
        public Call<Request, Response> withCallId(CallId callId) {
            return new Call<>(callId, serviceCallHolder, requestSerializer, responseSerializer, circuitBreaker,
                    autoAcl);
        }

        /**
         * Return a copy of this call descriptor with the given service call holder configured.
         *
         * @param serviceCallHolder
         *          The service call holder.
         * @return A copy of this call descriptor.
         */
        public Call<Request, Response> withServiceCallHolder(ServiceCallHolder serviceCallHolder) {
            return new Call<>(callId, serviceCallHolder, requestSerializer, responseSerializer, circuitBreaker,
                    autoAcl);
        }
    
        /**
         * Return a copy of this call descriptor with the given request message
         * serializer configured.
         *
         * @param requestSerializer
         *          The request serializer.
         * @return A copy of this call descriptor.
         */
        public Call<Request, Response> withRequestSerializer(MessageSerializer<Request, ?> requestSerializer) {
            return new Call<>(callId, serviceCallHolder, requestSerializer, responseSerializer, circuitBreaker,
                    autoAcl);
        }
    
        /**
         * Return a copy of this call descriptor with the given response message
         * serializer configured.
         *
         * @param responseSerializer
         *          The response serializer.
         * @return A copy of this call descriptor.
         */
        public Call<Request, Response> withResponseSerializer(MessageSerializer<Response, ?> responseSerializer) {
            return new Call<>(callId, serviceCallHolder, requestSerializer, responseSerializer, circuitBreaker,
                    autoAcl);
        }

        /**
         * Return a copy of this call descriptor with the given circuit breaker mode configured.
         *
         * @param circuitBreaker The circuit breaker mode.
         * @return A copy of this call descriptor.
         */
        public Call<Request, Response> withCircuitBreaker(CircuitBreaker circuitBreaker) {
            return new Call<>(callId, serviceCallHolder, requestSerializer, responseSerializer,
                    Optional.of(circuitBreaker), autoAcl);
        }

        /**
         * Return a copy of this call descriptor with autoAcl configured.
         *
         * @param autoAcl Whether an ACL will automatically be generated for the gateway to route calls to this service.
         * @return A copy of this call descriptor.
         */
        public Call<Request, Response> withAutoAcl(boolean autoAcl) {
            return new Call<>(callId, serviceCallHolder, requestSerializer, responseSerializer, circuitBreaker,
                    Optional.of(autoAcl));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Call)) return false;

            Call<?, ?> call = (Call<?, ?>) o;

            return callId.equals(call.callId);

        }

        @Override
        public int hashCode() {
            return callId.hashCode();
        }

        @Override
        public String toString() {
            return "Call{" +
                    "callId=" + callId +
                    ", serviceCallHolder=" + serviceCallHolder +
                    ", requestSerializer=" + requestSerializer +
                    ", responseSerializer=" + responseSerializer +
                    ", circuitBreaker=" + circuitBreaker +
                    ", autoAcl=" + autoAcl +
                    '}';
        }
    }

    /**
     * The Call ID.
     *
     * This is an abstract representation of how a service call is addressed within a service.  For example, in the
     * case of REST APIs, it will be addressed using an HTTP method and path.
     */
    public static abstract class CallId {
        private CallId() {}
    }

    /**
     * A REST call ID.
     */
    public static final class RestCallId extends CallId {
        private final Method method;
        private final String pathPattern;

        public RestCallId(Method method, String pathPattern) {
            this.method = method;
            this.pathPattern = pathPattern;
        }

        /**
         * The HTTP method for the call.
         *
         * The method will only be used for strict REST calls. For other calls, such as calls implemented by WebSockets
         * or other transports, the method may be ignored completely.
         *
         * @return The HTTP method.
         */
        public Method method() {
            return method;
        }

        /**
         * The path pattern for the call.
         *
         * @return The path pattern.
         */
        public String pathPattern() {
            return pathPattern;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RestCallId)) return false;

            RestCallId that = (RestCallId) o;

            if (!method.equals(that.method)) return false;
            return pathPattern.equals(that.pathPattern);

        }

        @Override
        public int hashCode() {
            int result = method.hashCode();
            result = 31 * result + pathPattern.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "RestCallId{" +
                    "method=" + method +
                    ", pathPattern='" + pathPattern + '\'' +
                    '}';
        }
    }

    /**
     * A path based call ID.
     */
    public static final class PathCallId extends CallId {
        private final String pathPattern;

        public PathCallId(String pathPattern) {
            this.pathPattern = pathPattern;
        }

        /**
         * Get the path pattern.
         *
         * @return The path pattern.
         */
        public String pathPattern() {
            return pathPattern;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PathCallId)) return false;

            PathCallId that = (PathCallId) o;

            return pathPattern.equals(that.pathPattern);

        }

        @Override
        public int hashCode() {
            return pathPattern.hashCode();
        }

        @Override
        public String toString() {
            return "PathCallId{" +
                    "pathPattern='" + pathPattern + '\'' +
                    '}';
        }
    }

    /**
     * A named call ID.
     */
    public static final class NamedCallId extends CallId {
        private final String name;

        public NamedCallId(String name) {
            this.name = name;
        }

        /**
         * Get the name of the call.
         *
         * @return The name of the call.
         */
        public String name() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NamedCallId)) return false;

            NamedCallId that = (NamedCallId) o;

            return name.equals(that.name);

        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return "NamedCallId{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }

    /**
     * Describes a topic call.
     */
    public static final class TopicCall<Message> {

        private final TopicId topicId;
        private final TopicHolder topicHolder;
        private final MessageSerializer<Message, ByteString> messageSerializer;
        private final Properties<Message> properties;

        TopicCall(TopicId topicId, TopicHolder topicHolder, MessageSerializer<Message, ByteString> messageSerializer, Properties<Message> properties) {
            this.topicId = topicId;
            this.topicHolder = topicHolder;
            this.messageSerializer = messageSerializer;
            this.properties = properties;
        }

        /**
         * Get the topic id.
         *
         * @return The topic id
         */
        public TopicId topicId() {
            return topicId;
        }

        /**
         * Get the topic holder.
         *
         * @return The topic holder.
         */
        public TopicHolder topicHolder() {
            return topicHolder;
        }

        /**
         * Get the topic's message serializer.
         *
         * @return The message serializer.
         */
        public MessageSerializer<Message, ByteString> messageSerializer() {
            return messageSerializer;
        }

        /**
         * Get the properties associated with this topic.
         *
         * @return The properties.
         */
        public Properties<Message> properties() {
            return properties;
        }

        /**
         * Return a copy of this topic call with the given topic holder configured.
         *
         * @param topicHolder The topic holder.
         * @return A copy of this topic call.
         */
        public TopicCall<Message> withTopicHolder(TopicHolder topicHolder) {
            return new TopicCall<>(topicId, topicHolder, messageSerializer, properties);
        }

        /**
         * Return a copy of this topic call with the given message serializer configured.
         *
         * @param messageSerializer A
         * @return A copy of this topic call.
         */
        public TopicCall<Message> withMessageSerializer(MessageSerializer<Message, ByteString> messageSerializer) {
            return new TopicCall<>(topicId, topicHolder, messageSerializer, properties);
        }

        /**
         * Return a copy of this copy call with the given property configured.
         *
         * @param property The property key.
         * @param value The value for the property.
         * @return A copy of this topic call.
         */
        public <T> TopicCall<Message> withProperty(Properties.Property<Message, T> property, T value) {
            return new TopicCall<>(topicId, topicHolder, messageSerializer, properties.withProperty(property, value));
        }
    }

    /**
     * Holds a map of properties.
     */
    public static final class Properties<Message> {
        /**
        * A property.
        */
        public static final class Property<Message, T> {
            private final Class<T> valueClass;
            private final String name;

            public Property(Class<T> valueClass, String name) {
                this.valueClass = valueClass;
                this.name = name;
            }

            public Class<T> valueClass() {
                return valueClass;
            }

            public String name() {
                return name;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Property<?, ?> property = (Property<?, ?>) o;

                if (!valueClass.equals(property.valueClass)) return false;
                return name.equals(property.name);
            }

            @Override
            public int hashCode() {
                int result = valueClass.hashCode();
                result = 31 * result + name.hashCode();
                return result;
            }

            @Override
            public String toString() {
                return "Property{" +
                        "valueClass=" + valueClass +
                        ", name='" + name + '\'' +
                        '}';
            }
        }

        private final PMap<Property<?, ?>, Object> properties;

        Properties(PMap<Property<?, ?>, Object> properties) {
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
        public <T> T getValueOf(Property<Message, T> property) {
            return (T) properties.get(property);
        }

        public <T> Properties<Message> withProperty(Property<Message, T> property, T value) {
            return new Properties<Message>(properties.plus(property, value));
        }
    }

    private final String name;
    private final PSequence<Call<?, ?>> calls;
    private final PMap<Type, PathParamSerializer<?>> pathParamSerializers;
    private final PMap<Type, MessageSerializer<?, ?>> messageSerializers;
    private final SerializerFactory serializerFactory;
    private final ExceptionSerializer exceptionSerializer;
    private final boolean autoAcl;
    private final PSequence<ServiceAcl> acls;
    private final HeaderFilter headerFilter;
    private final boolean locatableService;
    private final CircuitBreaker circuitBreaker;
    private final PSequence<TopicCall<?>> topicCalls;

    Descriptor(String name) {
        this(name, TreePVector.empty(), HashTreePMap.empty(), HashTreePMap.empty(), SerializerFactory.DEFAULT,
                ExceptionSerializer.DEFAULT, false, TreePVector.empty(),
                new UserAgentHeaderFilter(), true, CircuitBreaker.perNode(), TreePVector.empty());
    }

    Descriptor(String name, PSequence<Call<?, ?>> calls, PMap<Type, PathParamSerializer<?>> pathParamSerializers,
            PMap<Type, MessageSerializer<?, ?>> messageSerializers, SerializerFactory serializerFactory,
            ExceptionSerializer exceptionSerializer, boolean autoAcl, PSequence<ServiceAcl> acls, 
            HeaderFilter headerFilter, boolean locatableService, CircuitBreaker circuitBreaker, PSequence<TopicCall<?>> topicCalls) {
        this.name = name;
        this.calls = calls;
        this.pathParamSerializers = pathParamSerializers;
        this.messageSerializers = messageSerializers;
        this.serializerFactory = serializerFactory;
        this.exceptionSerializer = exceptionSerializer;
        this.autoAcl = autoAcl;
        this.acls = acls;
        this.headerFilter = headerFilter;
        this.locatableService = locatableService;
        this.circuitBreaker = circuitBreaker;
        this.topicCalls = topicCalls;
    }

    public String name() {
        return name;
    }

    public PSequence<Call<?, ?>> calls() {
        return calls;
    }

    public PMap<Type, PathParamSerializer<?>> pathParamSerializers() {
        return pathParamSerializers;
    }

    public PMap<Type, MessageSerializer<?, ?>> messageSerializers() {
        return messageSerializers;
    }

    public SerializerFactory serializerFactory() {
        return serializerFactory;
    }

    public ExceptionSerializer exceptionSerializer() {
        return exceptionSerializer;
    }

    /**
     * Whether this descriptor will auto generate ACLs for each call.
     */
    public boolean autoAcl() {
        return autoAcl;
    }

    /**
     * The manually configured ACLs for this service.
     */
    public PSequence<ServiceAcl> acls() {
        return acls;
    }

    /**
     * The header filter to use for this service.
     */
    public HeaderFilter headerFilter() {
        return headerFilter;
    }
    
    /**
     * Whether this is a locatable service.
     *
     * Locatable services are registered with the service locator and/or gateway, so that they can be consumed by other
     * services.  Services that are not locatable are typically services for infrastructure purposes, such as providing
     * metrics.
     */
    public boolean locatableService() {
        return locatableService;
    }

    /**
     * Get the default circuit breaker mode used by this service.
     *
     * This is what will be used if no service breaker is explicitly set on the service call.
     *
     * @return The circuit breaker to use.
     */
    public CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    public PSequence<TopicCall<?>> topicCalls() {
      return topicCalls;
    }

    /**
     * Provide a custom path param serializer for the given path param type.
     *
     * @param pathParamType The path param type.
     * @param pathParamSerializer The path param serializer.
     * @return A copy of this descriptor.
     */
    public <T> Descriptor withPathParamSerializer(Class<T> pathParamType, PathParamSerializer<T> pathParamSerializer) {
        return withPathParamSerializer((Type) pathParamType, pathParamSerializer);
    }

    /**
     * Provide a custom path param serializer for the given path param type.
     *
     * @param pathParamType The path param type.
     * @param pathParamSerializer The path param serializer.
     * @return A copy of this descriptor.
     */
    public Descriptor withPathParamSerializer(Type pathParamType, PathParamSerializer<?> pathParamSerializer) {
        return replaceAllPathParamSerializers(pathParamSerializers.plus(pathParamType, pathParamSerializer));
    }

    /**
     * Provide a custom MessageSerializer for the given message type.
     *
     * @param messageType The type of the message.
     * @param messageSerializer The message serializer for that type.
     * @return A copy of this descriptor.
     */
    public <T> Descriptor withMessageSerializer(Class<T> messageType, MessageSerializer<T, ?> messageSerializer) {
        return withMessageSerializer((Type) messageType, messageSerializer);
    }

    /**
     * Provide a custom MessageSerializer for the given message type.
     *
     * @param messageType The type of the message.
     * @param messageSerializer The message serializer for that type.
     * @return A copy of this descriptor.
     */
    public Descriptor withMessageSerializer(Type messageType, MessageSerializer<?, ?> messageSerializer) {
        return replaceAllMessageSerializers(messageSerializers.plus(messageType, messageSerializer));
    }

    /**
     * Add the given service calls to this service.
     *
     * @param calls The calls to add.
     * @return A copy of this descriptor with the new calls added.
     */
    public Descriptor withCalls(Call<?, ?>... calls) {
        return replaceAllCalls(this.calls.plusAll(Arrays.asList(calls)));
    }

    /**
     * Replace all the service calls provided by this descriptor with the the given service calls.
     *
     * @param calls The calls to replace the existing ones with.
     * @return A copy of this descriptor with the new calls.
     */
    public Descriptor replaceAllCalls(PSequence<Call<?, ?>> calls) {
        return new Descriptor(name, calls, pathParamSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, headerFilter, locatableService, circuitBreaker, topicCalls);
    }

    /**
     * Replace all the path param serializers registered with this descriptor with the the given path param serializers.
     *
     * @param pathParamSerializers The path param serializers to replace the existing ones with.
     * @return A copy of this descriptor with the new path param serializers.
     */
    public Descriptor replaceAllPathParamSerializers(PMap<Type, PathParamSerializer<?>> pathParamSerializers) {
        return new Descriptor(name, calls, pathParamSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, headerFilter, locatableService, circuitBreaker, topicCalls);
    }

    /**
     * Replace all the message serializers registered with this descriptor with the the given message serializers.
     *
     * @param messageSerializers The message serializers to replace the existing ones with.
     * @return A copy of this descriptor with the new message serializers.
     */
    public Descriptor replaceAllMessageSerializers(PMap<Type, MessageSerializer<?, ?>> messageSerializers) {
        return new Descriptor(name, calls, pathParamSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, headerFilter, locatableService, circuitBreaker, topicCalls);
    }

    /**
     * Replace all the topic calls provided by this descriptor with the the given topic calls.
     *
     * @param topicCalls The topic calls to replace the existing ones with.
     * @return A copy of this descriptor with the new topic calls.
     */
    public Descriptor replaceAllTopicCalls(PSequence<TopicCall<?>> topicCalls) {
        return new Descriptor(name, calls, pathParamSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, headerFilter, locatableService, circuitBreaker, topicCalls);
    }

    /**
     * Use the given exception serializer to serialize and deserialized exceptions handled by this service.
     *
     * @param exceptionSerializer The exception handler to use.
     * @return A copy of this descriptor.
     */
    public Descriptor withExceptionSerializer(ExceptionSerializer exceptionSerializer) {
        return new Descriptor(name, calls, pathParamSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, headerFilter, locatableService, circuitBreaker, topicCalls);
    }

    /**
     * Use the given serializer factory with this service.
     *
     * @param serializerFactory The serializer factory to use.
     * @return A copy of this descriptor.
     */
    public Descriptor withSerializerFactory(SerializerFactory serializerFactory) {
        return new Descriptor(name, calls, pathParamSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, headerFilter, locatableService, circuitBreaker, topicCalls);
    }

    /**
     * Set whether the service calls in this descriptor should default to having an ACL automatically generated for
     * them.
     *
     * By default, this will not happen.
     *
     * Note that each service call can override this by calling withAutoAcl on them.
     *
     * @param autoAcl Whether autoAcl should be true.
     * @return A copy of this descriptor.
     */
    public Descriptor withAutoAcl(boolean autoAcl) {
        return new Descriptor(name, calls, pathParamSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, headerFilter, locatableService, circuitBreaker, topicCalls);
    }


    /**
     * Add the given manual ACLs.
     *
     * If auto ACLs are configured, these will be added in addition to the auto ACLs.
     *
     * @param acls The ACLs to add.
     * @return A copy of this descriptor.
     */
    public Descriptor withServiceAcls(ServiceAcl... acls) {
        return replaceAllAcls(this.acls.plusAll(Arrays.asList(acls)));
    }

    /**
     * Replace all the ACLs with the given ACL sequence.
     *
     * This will not replace ACLs generated by autoAcl, to disable autoAcl, turn it off.
     *
     * @param acls The ACLs to use.
     * @return A copy of this descriptor.
     */
    public Descriptor replaceAllAcls(PSequence<ServiceAcl> acls) {
        return new Descriptor(name, calls, pathParamSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, headerFilter, locatableService, circuitBreaker, topicCalls);
    }

    /**
     * Configure the given header filter.
     *
     * @param headerFilter The header filter to use.
     * @return A copy of this descriptor.
     */
    public Descriptor withHeaderFilter(HeaderFilter headerFilter) {
        return new Descriptor(name, calls, pathParamSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, headerFilter, locatableService, circuitBreaker, topicCalls);
    }

    /**
     * Set whether this service is locatable.
     *
     * Locatable services are registered with the service locator and/or gateway, so that they can be consumed by other
     * services.  Services that are not locatable are typically services for infrastructure purposes, such as providing
     * metrics.
     *
     * @param locatableService Whether this service should be locatable or not.
     * @return A copy of this descriptor.
     */
    public Descriptor withLocatableService(boolean locatableService) {
        return new Descriptor(name, calls, pathParamSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, headerFilter, locatableService, circuitBreaker, topicCalls);
    }

    /**
     * Set the default circuit breaker to use for this service.
     *
     * This circuit breaker mode will be used for any service calls that don't explicitly configure their own circuit
     * breaker configuration.
     *
     * @param circuitBreaker The circuit breaker mode.
     * @return A copy of this descriptor.
     */
    public Descriptor withCircuitBreaker(CircuitBreaker circuitBreaker) {
        return new Descriptor(name, calls, pathParamSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, headerFilter, locatableService, circuitBreaker, topicCalls);
    }

    /**
     * Add the given topic calls to this service.
     *
     * @param topicCalls The topic calls to add.
     * @return A copy of this descriptor with the new calls added.
     */
    public Descriptor publishing(TopicCall<?>... topicCalls) {
      return new Descriptor(name, calls, pathParamSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, headerFilter, locatableService, circuitBreaker,
          this.topicCalls.plusAll(Arrays.asList(topicCalls)));
    }
}
