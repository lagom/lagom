/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api;

import java.util.Optional;

import com.lightbend.lagom.javadsl.api.deser.*;
import com.lightbend.lagom.javadsl.api.security.UserAgentServiceIdentificationStrategy;
import com.lightbend.lagom.javadsl.api.transport.HeaderTransformer;
import com.lightbend.lagom.javadsl.api.transport.Method;
import com.lightbend.lagom.javadsl.api.transport.PathVersionedProtocolNegotiationStrategy;
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
     * Describes a service call.
     */
    public static final class Call<Id, Request, Response> {
        private final CallId callId;
        private final ServiceCall<Id, Request, Response> serviceCall;
        private final IdSerializer<Id> idSerializer;
        private final MessageSerializer<Request, ?> requestSerializer;
        private final MessageSerializer<Response, ?> responseSerializer;
        private final Optional<CircuitBreakerId> circuitBreaker;
        private final Optional<Boolean> autoAcl;


        Call(CallId callId, ServiceCall<Id, Request, Response> serviceCall,
             IdSerializer<Id> idSerializer, MessageSerializer<Request, ?> requestSerializer,
             MessageSerializer<Response, ?> responseSerializer, Optional<CircuitBreakerId> circuitBreaker,
             Optional<Boolean> autoAcl) {

            this.callId = callId;
            this.serviceCall = serviceCall;
            this.idSerializer = idSerializer;
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
         * Get the service call itself.
         *
         * @return The service call.
         */
        public ServiceCall<Id, Request, Response> serviceCall() {
            return serviceCall;
        }

        /**
         * Get the ID serializer.
         *
         * @return The ID serializer.
         */
        public IdSerializer<Id> idSerializer() {
            return idSerializer;
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
         * Get the circuit breaker identifier.
         *
         * @return The circuit breaker identifier.
         */
        public Optional<CircuitBreakerId> circuitBreaker() {
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
         * Return a copy of this call descriptor with the given ID serializer configured.
         *
         * @param idSerializer
         *          The ID serializer.
         * @return A copy of this call descriptor.
         */
        public Call<Id, Request, Response> with(IdSerializer<Id> idSerializer) {
            return new Call<>(callId, serviceCall, idSerializer, requestSerializer, responseSerializer, circuitBreaker,
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
        public Call<Id, Request, Response> withRequestSerializer(MessageSerializer<Request, ?> requestSerializer) {
            return new Call<>(callId, serviceCall, idSerializer, requestSerializer, responseSerializer, circuitBreaker,
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
        public Call<Id, Request, Response> withResponseSerializer(MessageSerializer<Response, ?> responseSerializer) {
            return new Call<>(callId, serviceCall, idSerializer, requestSerializer, responseSerializer, circuitBreaker,
                    autoAcl);
        }
    
        /**
         * Return a copy of this call descriptor with the given service call
         * configured.
         *
         * @param serviceCall
         *          The service call.
         * @return A copy of this call descriptor.
         */
        public Call<Id, Request, Response> with(ServiceCall<Id, Request, Response> serviceCall) {
            return new Call<>(callId, serviceCall, idSerializer, requestSerializer, responseSerializer, circuitBreaker,
                    autoAcl);
        }
    
        /**
         * Return a copy of this call descriptor with the given circuit breaker
         * identifier configured.
         *
         * @param breakerId
         *          The configuration id of the circuit breaker
         * @return A copy of this call descriptor.
         */
        public Call<Id, Request, Response> withCircuitBreaker(CircuitBreakerId breakerId) {
            return new Call<>(callId, serviceCall, idSerializer, requestSerializer, responseSerializer,
                    Optional.ofNullable(breakerId), autoAcl);
        }

        /**
         * Return a copy of this call descriptor with autoAcl configured.
         *
         * @param autoAcl Whether an ACL will automatically be generated for the gateway to route calls to this service.
         * @return A copy of this call descriptor.
         */
        public Call<Id, Request, Response> withAutoAcl(boolean autoAcl) {
            return new Call<>(callId, serviceCall, idSerializer, requestSerializer, responseSerializer, circuitBreaker,
                    Optional.of(autoAcl));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Call)) return false;

            Call<?, ?, ?> call = (Call<?, ?, ?>) o;

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
                    ", serviceCall=" + serviceCall +
                    ", idSerializer=" + idSerializer +
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
    public static class PathCallId extends CallId {
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
    public static class NamedCallId extends CallId {
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
    
    public static class CircuitBreakerId {
        private final String id;
  
        public CircuitBreakerId(String id) {
            this.id = id;
        }
  
        /**
         * Get the identifier of the circuit breaker
         */
        public String id() {
            return id;
        }
  
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CircuitBreakerId)) return false;
  
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

    private final String name;
    private final PSequence<Call<?, ?, ?>> calls;
    private final PMap<Type, IdSerializer<?>> idSerializers;
    private final PMap<Type, MessageSerializer<?, ?>> messageSerializers;
    private final SerializerFactory serializerFactory;
    private final ExceptionSerializer exceptionSerializer;
    private final boolean autoAcl;
    private final PSequence<ServiceAcl> acls;
    private final HeaderTransformer protocolNegotiationStrategy;
    private final HeaderTransformer serviceIdentificationStrategy;
    private final boolean locatableService;

    Descriptor(String name) {
        this(name, TreePVector.empty(), HashTreePMap.empty(), HashTreePMap.empty(), SerializerFactory.DEFAULT,
                ExceptionSerializer.DEFAULT, false, TreePVector.empty(), new PathVersionedProtocolNegotiationStrategy(),
                new UserAgentServiceIdentificationStrategy(), true);
    }

    Descriptor(String name, PSequence<Call<?, ?, ?>> calls, PMap<Type, IdSerializer<?>> idSerializers,
            PMap<Type, MessageSerializer<?, ?>> messageSerializers, SerializerFactory serializerFactory,
            ExceptionSerializer exceptionSerializer, boolean autoAcl, PSequence<ServiceAcl> acls, HeaderTransformer protocolNegotiationStrategy,
            HeaderTransformer serviceIdentificationStrategy, boolean locatableService) {
        this.name = name;
        this.calls = calls;
        this.idSerializers = idSerializers;
        this.messageSerializers = messageSerializers;
        this.serializerFactory = serializerFactory;
        this.exceptionSerializer = exceptionSerializer;
        this.autoAcl = autoAcl;
        this.acls = acls;
        this.protocolNegotiationStrategy = protocolNegotiationStrategy;
        this.serviceIdentificationStrategy = serviceIdentificationStrategy;
        this.locatableService = locatableService;
    }

    public String name() {
        return name;
    }

    public PSequence<Call<?, ?, ?>> calls() {
        return calls;
    }

    public PMap<Type, IdSerializer<?>> idSerializers() {
        return idSerializers;
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

    public HeaderTransformer protocolNegotiationStrategy() {
        return protocolNegotiationStrategy;
    }

    public HeaderTransformer serviceIdentificationStrategy() {
        return serviceIdentificationStrategy;
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

    public <T> Descriptor with(Class<T> idType, IdSerializer<T> idSerializer) {
        return with((Type) idType, idSerializer);
    }

    public Descriptor with(Type idType, IdSerializer<?> idSerializer) {
        return replaceAllIdSerializers(idSerializers.plus(idType, idSerializer));
    }

    public <T> Descriptor with(Class<T> messageType, MessageSerializer<T, ?> messageSerializer) {
        return with((Type) messageType, messageSerializer);
    }

    public Descriptor with(Type messageType, MessageSerializer<?, ?> messageSerializer) {
        return replaceAllMessageSerializers(messageSerializers.plus(messageType, messageSerializer));
    }

    public Descriptor with(Call<?, ?, ?>... calls) {
        return replaceAllCalls(this.calls.plusAll(Arrays.asList(calls)));
    }

    public Descriptor replaceAllCalls(PSequence<Call<?, ?, ?>> calls) {
        return new Descriptor(name, calls, idSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, protocolNegotiationStrategy, serviceIdentificationStrategy, locatableService);
    }

    public Descriptor replaceAllIdSerializers(PMap<Type, IdSerializer<?>> idSerializers) {
        return new Descriptor(name, calls, idSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, protocolNegotiationStrategy, serviceIdentificationStrategy, locatableService);
    }

    public Descriptor replaceAllMessageSerializers(PMap<Type, MessageSerializer<?, ?>> messageSerializers) {
        return new Descriptor(name, calls, idSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, protocolNegotiationStrategy, serviceIdentificationStrategy, locatableService);
    }

    public Descriptor with(ExceptionSerializer exceptionSerializer) {
        return new Descriptor(name, calls, idSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, protocolNegotiationStrategy, serviceIdentificationStrategy, locatableService);
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
        return new Descriptor(name, calls, idSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, protocolNegotiationStrategy, serviceIdentificationStrategy, locatableService);
    }


    /**
     * Add the given manual ACLs.
     *
     * If auto ACLs are configured, these will be added in addition to the auto ACLs.
     *
     * @param acls The ACLs to add.
     * @return A copy of this descriptor.
     */
    public Descriptor with(ServiceAcl... acls) {
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
        return new Descriptor(name, calls, idSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, protocolNegotiationStrategy, serviceIdentificationStrategy, locatableService);
    }


    public Descriptor withProtocolNegotiationStrategy(HeaderTransformer protocolNegotiationStrategy) {
        return new Descriptor(name, calls, idSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, protocolNegotiationStrategy, serviceIdentificationStrategy, locatableService);
    }

    public Descriptor withServiceIdentificationStrategy(HeaderTransformer serviceIdentificationStrategy) {
        return new Descriptor(name, calls, idSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, protocolNegotiationStrategy, serviceIdentificationStrategy, locatableService);
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
        return new Descriptor(name, calls, idSerializers, messageSerializers, serializerFactory, exceptionSerializer, autoAcl, acls, protocolNegotiationStrategy, serviceIdentificationStrategy, locatableService);
    }


}
