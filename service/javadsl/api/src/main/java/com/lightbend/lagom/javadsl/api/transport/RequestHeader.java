/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.transport;

import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import java.net.URI;
import java.security.Principal;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * A request header.
 *
 * This header may or may not be mapped down onto HTTP.  In order to remain agnostic to the underlying protocol,
 * information required by Lagom, such as protocol information, is extracted.  It is encouraged that the protocol
 * information always be used in preference to reading the information directly out of headers, since the headers may
 * not contain the necessary protocol information.
 *
 * The headers are however still provided, in case information needs to be extracted out of non standard headers.
 */
public final class RequestHeader extends MessageHeader {
    private final Method method;
    private final URI uri;
    private final PSequence<MessageProtocol> acceptedResponseProtocols;
    private final Optional<Principal> principal;

    private RequestHeader(Method method, URI uri, MessageProtocol protocol, PSequence<MessageProtocol> acceptedResponseProtocols, Optional<Principal> principal, PMap<String, PSequence<String>> headers, PMap<String, PSequence<String>> lowercaseHeaders) {
        super(protocol, headers, lowercaseHeaders);
        this.method = method;
        this.uri = uri;
        this.acceptedResponseProtocols = acceptedResponseProtocols;
        this.principal = principal;
    }

    public RequestHeader(Method method, URI uri, MessageProtocol protocol, PSequence<MessageProtocol> acceptedResponseProtocols, Optional<Principal> principal, PMap<String, PSequence<String>> headers) {
        super(protocol, headers);
        this.method = method;
        this.uri = uri;
        this.acceptedResponseProtocols = acceptedResponseProtocols;
        this.principal = principal;
    }

    /**
     * Get the method used to make this request.
     *
     * @return The method.
     */
    public Method method() {
        return method;
    }

    /**
     * Get the URI for this request.
     *
     * @return The URI.
     */
    public URI uri() {
        return uri;
    }

    /**
     * Get the accepted response protocols for this request.
     *
     * @return The accepted response protocols.
     */
    public PSequence<MessageProtocol> acceptedResponseProtocols() {
        return acceptedResponseProtocols;
    }

    /**
     * Get the principal for this request, if there is one.
     *
     * @return The principal for this request.
     */
    public Optional<Principal> principal() {
        return principal;
    }

    /**
     * Return a copy of this request header with the given method set.
     *
     * @param method The method to set.
     * @return A copy of this request header.
     */
    public RequestHeader withMethod(Method method) {
        return new RequestHeader(method, uri, protocol, acceptedResponseProtocols, principal, headers, lowercaseHeaders);
    }

    /**
     * Return a copy of this request header with the given uri set.
     *
     * @param uri The uri to set.
     * @return A copy of this request header.
     */
    public RequestHeader withUri(URI uri) {
        return new RequestHeader(method, uri, protocol, acceptedResponseProtocols, principal, headers, lowercaseHeaders);
    }

    @Override
    public RequestHeader withProtocol(MessageProtocol protocol) {
        return new RequestHeader(method, uri, protocol, acceptedResponseProtocols, principal, headers, lowercaseHeaders);
    }

    /**
     * Return a copy of this request header with the given accepted response protocols set.
     *
     * @param acceptedResponseProtocols The accepted response protocols to set.
     * @return A copy of this request header.
     */
    public RequestHeader withAcceptedResponseProtocols(PSequence<MessageProtocol> acceptedResponseProtocols) {
        return new RequestHeader(method, uri, protocol, acceptedResponseProtocols, principal, headers, lowercaseHeaders);
    }

    /**
     * Return a copy of this request header with the principal set.
     *
     * @param principal The principal to set.
     * @return A copy of this request header.
     */
    public RequestHeader withPrincipal(Principal principal) {
        return new RequestHeader(method, uri, protocol, acceptedResponseProtocols, Optional.ofNullable(principal), headers, lowercaseHeaders);
    }


    /**
     * Return a copy of this request header with the principal cleared.
     *
     * @return A copy of this request header.
     */
    public RequestHeader clearPrincipal() {
        return new RequestHeader(method, uri, protocol, acceptedResponseProtocols, Optional.empty(), headers, lowercaseHeaders);
    }

    @Override
    public RequestHeader replaceAllHeaders(PMap<String, PSequence<String>> headers) {
        return new RequestHeader(method, uri, protocol, acceptedResponseProtocols, principal, headers);
    }

    @Override
    public RequestHeader withHeader(String name, String value) {
        return new RequestHeader(method, uri, protocol, acceptedResponseProtocols, principal,
                headers.plus(name, TreePVector.singleton(value)),
                lowercaseHeaders.plus(name.toLowerCase(Locale.ENGLISH), TreePVector.singleton(value)));
    }

    @Override
    public String toString() {
        return "RequestHeader{" +
                "method=" + method +
                ", uri=" + uri +
                ", protocol=" + protocol +
                ", acceptedResponseProtocols=" + acceptedResponseProtocols +
                ", principal=" + principal +
                ", headers=" + headers +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RequestHeader)) return false;

        RequestHeader that = (RequestHeader) o;

        if (!method.equals(that.method)) return false;
        if (!uri.equals(that.uri)) return false;
        if (!protocol.equals(that.protocol)) return false;
        if (!acceptedResponseProtocols.equals(that.acceptedResponseProtocols)) return false;
        if (!principal.equals(that.principal)) return false;
        return lowercaseHeaders.equals(that.lowercaseHeaders);

    }

    @Override
    public int hashCode() {
        int result = method.hashCode();
        result = 31 * result + uri.hashCode();
        result = 31 * result + protocol.hashCode();
        result = 31 * result + acceptedResponseProtocols.hashCode();
        result = 31 * result + principal.hashCode();
        result = 31 * result + lowercaseHeaders.hashCode();
        return result;
    }

    /**
     * A default request header object.
     *
     * This is a convenience supplied so that server implementations of service calls can pass this request to the
     * request header handler, in order to get the actual incoming request header.
     *
     * See {@link com.lightbend.lagom.javadsl.api.ServiceCall#handleRequestHeader(Function)}
     */
    public static final RequestHeader DEFAULT = new RequestHeader(Method.GET, URI.create("/"),
        new MessageProtocol(), TreePVector.empty(), Optional.empty(), HashTreePMap.empty());
}
