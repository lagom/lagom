/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.transport;

import java.nio.charset.Charset;
import java.util.Optional;

/**
 * A message protocol.
 *
 * This describes the negotiated protocol being used for a message.  It has three elements, a content type, a charset,
 * and a version.
 *
 * The <tt>contentType</tt> may be registered mime type such as <tt>application/json</tt>, or it could be an application
 * specific content type, such as <tt>application/vnd.myservice+json</tt>.  It could also contain protocol versioning
 * information, such as <tt>application/vnd.github.v3+json</tt>.  During the protocol negotiation process, the
 * content type may be transformed, for example, if the content type contains a version, the configured
 * {@link HeaderFilter} will be expected to extract that version
 * out into the <tt>version</tt>, leaving a <tt>contentType</tt> that will be understood by the message serializer.
 *
 * The <tt>charset</tt> applies to text messages, if the message is not in a text format, then no <tt>charset</tt>
 * should be specified.  This is not only used in setting of content negotiation headers, it's also used as a hint to
 * the framework of when it can treat a message as text.  For example, if the charset is set, then when a message gets
 * sent via WebSockets, it will be sent as a text message, otherwise it will be sent as a binary message.
 *
 * The <tt>version</tt> is used to describe the version of the protocol being used. Lagom does not, out of the box,
 * prescribe any semantics around the version, from Lagom's perspective, two message protocols with different versions
 * are two different protocols. The version is however treated as a separate piece of information so that generic
 * parsers, such as json/xml, can make sensible use of the content type passed to them.  The version could come from
 * a media type header, but it does not necessarily have to come from there, it could come from the URI or any other
 * header.
 *
 * <tt>MessageProtocol</tt> instances can also be used in content negotiation, an empty value means that any value
 * is accepted.
 */
public final class MessageProtocol {

    private final Optional<String> contentType;
    private final Optional<String> charset;
    private final Optional<String> version;

    /**
     * Create a message protocol with the given content type, charset and version.
     *
     * @param contentType The content type.
     * @param charset The charset.
     * @param version The version.
     */
    public MessageProtocol(Optional<String> contentType, Optional<String> charset, Optional<String> version) {
        this.contentType = contentType;
        this.charset = charset;
        this.version = version;
    }

    /**
     * Create a message protocol that doesn't specify any content type, charset or version.
     */
    public MessageProtocol() {
        contentType = Optional.empty();
        charset = Optional.empty();
        version = Optional.empty();
    }

    /**
     * The content type of the protocol.
     *
     * @return The content type.
     */
    public Optional<String> contentType() {
        return contentType;
    }

    /**
     * The charset of the protocol.
     *
     * @return The charset.
     */
    public Optional<String> charset() {
        return charset;
    }

    /**
     * The version of the protocol.
     *
     * @return The version.
     */
    public Optional<String> version() {
        return version;
    }

    /**
     * Return a copy of this message protocol with the content type set to the given content type.
     *
     * @param contentType The content type to set.
     * @return A copy of this message protocol.
     */
    public MessageProtocol withContentType(String contentType) {
        return new MessageProtocol(Optional.ofNullable(contentType), charset, version);
    }

    /**
     * Return a copy of this message protocol with the charset set to the given charset.
     *
     * @param charset The charset to set.
     * @return A copy of this message protocol.
     */
    public MessageProtocol withCharset(String charset) {
        return new MessageProtocol(contentType, Optional.ofNullable(charset), version);
    }

    /**
     * Return a copy of this message protocol with the version set to the given version.
     *
     * @param version The version to set.
     * @return A copy of this message protocol.
     */
    public MessageProtocol withVersion(String version) {
        return new MessageProtocol(contentType, charset, Optional.ofNullable(version));
    }

    /**
     * Whether this message protocol is a text based protocol.
     *
     * This is determined by whether the charset is defined.
     *
     * @return true if this message protocol is text based.
     */
    public boolean isText() {
        return charset.isPresent();
    }

    private static final Charset utf8Charset = Charset.forName("utf-8");

    /**
     * Whether the protocol uses UTF-8.
     *
     * @return true if the charset used by this protocol is UTF-8, false if it's some other encoding or if no charset is
     *      defined.
     */
    public boolean isUtf8() {
        if (charset.isPresent()) {
            return utf8Charset.equals(Charset.forName(charset.get()));
        } else {
            return false;
        }
    }

    /**
     * Convert this message protocol to a content type header, if the content type is defined.
     *
     * @return The message protocol as a content type header.
     */
    public Optional<String> toContentTypeHeader() {
        return contentType.map(ct -> {
            if (charset.isPresent()) {
                return ct + "; charset=" + charset.get();
            } else {
                return ct;
            }
        });
    }

    /**
     * Parse a message protocol from a content type header, if defined.
     *
     * @param contentType The content type header to parse.
     * @return The parsed message protocol.
     */
    public static MessageProtocol fromContentTypeHeader(Optional<String> contentType) {
        return contentType.map(ct -> {
            String[] parts = ct.split(";");
            String justContentType = parts[0];
            Optional<String> charset = Optional.empty();
            for (int i = 1; i < parts.length; i++) {
                String toTest = parts[i].trim();
                if (toTest.startsWith("charset=")) {
                    charset = Optional.of(toTest.split("=", 2)[1]);
                    break;
                }
            }
            return new MessageProtocol(Optional.of(justContentType), charset, Optional.empty());
        }).orElse(new MessageProtocol());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MessageProtocol that = (MessageProtocol) o;

        if (!contentType.equals(that.contentType)) return false;
        if (!charset.equals(that.charset)) return false;
        return version.equals(that.version);

    }

    @Override
    public int hashCode() {
        int result = contentType.hashCode();
        result = 31 * result + charset.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "MessageProtocol{" +
                "contentType=" + contentType +
                ", charset=" + charset +
                ", version=" + version +
                '}';
    }
}
