/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.transport;

import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.pcollections.PSequence;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A message header.
 */
public abstract class MessageHeader {
    protected final MessageProtocol protocol;
    protected final PMap<String, PSequence<String>> headers;
    protected final PMap<String, PSequence<String>> lowercaseHeaders;

    MessageHeader(MessageProtocol protocol, PMap<String, PSequence<String>> headers,
            PMap<String, PSequence<String>> lowercaseHeaders) {
        this.protocol = protocol;
        this.headers = headers;
        this.lowercaseHeaders = lowercaseHeaders;
    }

    MessageHeader(MessageProtocol protocol, PMap<String, PSequence<String>> headers) {
        this.protocol = protocol;
        this.headers = headers;
        PMap<String, PSequence<String>> lowercaseHeaders = HashTreePMap.empty();
        for (Map.Entry<String, PSequence<String>> header: headers.entrySet()) {
            lowercaseHeaders = lowercaseHeaders.plus(header.getKey().toLowerCase(Locale.ENGLISH), header.getValue());
        }
        this.lowercaseHeaders = lowercaseHeaders;
    }

    /**
     * Get the protocol of the message.
     *
     * @return The protocol.
     */
    public MessageProtocol protocol() {
        return protocol;
    }

    /**
     * Get the headers for the message.
     *
     * The returned map is case sensitive, it is recommended that you use <tt>getHeader</tt> instead.
     *
     * @return The headers for this message.
     */
    public PMap<String, PSequence<String>> headers() {
        return headers;
    }

    /**
     * Get the header with the given name.
     *
     * The lookup is case insensitive.
     *
     * @param name The name of the header.
     * @return The header value.
     */
    public Optional<String> getHeader(String name) {
        PSequence<String> values = lowercaseHeaders.get(name.toLowerCase(Locale.ENGLISH));
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(values.get(0));
        }
    }

    /**
     * Return a copy of this message header with the given protocol.
     *
     * @param protocol The protocol to set.
     * @return A copy of the message header with the given protocol.
     */
    public abstract MessageHeader withProtocol(MessageProtocol protocol);

    /**
     * Return a copy of this message header with the headers replaced by the given map of headers.
     *
     * @param headers The map of headers.
     * @return A copy of the message header with the given headers.
     */
    public abstract MessageHeader replaceAllHeaders(PMap<String, PSequence<String>> headers);

    /**
     * Return a copy of this message header with the given header added to the map of headers.
     *
     * If the header already has a value, this value will replace it.
     *
     * @param name The name of the header to add.
     * @param value The value of the header to add.
     * @return The new message header.
     */
    public abstract MessageHeader withHeader(String name, String value);

}
