/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.transport;

import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import java.util.Locale;

/**
 * This header may or may not be mapped down onto HTTP.  In order to remain agnostic to the underlying protocol,
 * information required by Lagom, such as protocol information, is extracted.  It is encouraged that the protocol
 * information always be used in preference to reading the information directly out of headers, since the headers may
 * not contain the necessary protocol information.
 *
 * The headers are however still provided, in case information needs to be extracted out of non standard headers.
 */
public final class ResponseHeader extends MessageHeader {
    private final int status;

    private ResponseHeader(int status, MessageProtocol protocol, PMap<String, PSequence<String>> headers, PMap<String, PSequence<String>> lowercaseHeaders) {
        super(protocol, headers, lowercaseHeaders);
        this.status = status;
    }

    public ResponseHeader(int status, MessageProtocol protocol, PMap<String, PSequence<String>> headers) {
        super(protocol, headers);
        this.status = status;
    }

    /**
     * Get the status of this response.
     *
     * @return The status of this response.
     */
    public int status() {
        return status;
    }

    /**
     * Return a copy of this response header with the status set.
     *
     * @param status The status to set.
     * @return A copy of this response header.
     */
    public ResponseHeader withStatus(int status) {
        return new ResponseHeader(status, protocol, headers, lowercaseHeaders);
    }

    @Override
    public ResponseHeader withProtocol(MessageProtocol protocol) {
        return new ResponseHeader(status, protocol, headers, lowercaseHeaders);
    }

    @Override
    public ResponseHeader replaceAllHeaders(PMap<String, PSequence<String>> headers) {
        return new ResponseHeader(status, protocol, headers);
    }

    @Override
    public ResponseHeader withHeader(String name, String value) {
        return new ResponseHeader(status, protocol,
                headers.plus(name, TreePVector.singleton(value)),
                lowercaseHeaders.plus(name.toLowerCase(Locale.ENGLISH), TreePVector.singleton(value)));
    }

    public static final ResponseHeader OK = new ResponseHeader(200, new MessageProtocol(), HashTreePMap.empty());
    public static final ResponseHeader NO_CONTENT = new ResponseHeader(204, new MessageProtocol(), HashTreePMap.empty());

    @Override
    public String toString() {
        return "ResponseHeader{" +
                "status=" + status +
                ", protocol=" + protocol +
                ", headers=" + headers +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResponseHeader)) return false;

        ResponseHeader that = (ResponseHeader) o;

        if (status != that.status) return false;
        if (!protocol.equals(that.protocol)) return false;
        return lowercaseHeaders.equals(that.lowercaseHeaders);
    }

    @Override
    public int hashCode() {
        int result = status;
        result = 31 * result + protocol.hashCode();
        result = 31 * result + lowercaseHeaders.hashCode();
        return result;
    }
}
