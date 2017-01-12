/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.deser;

import akka.util.ByteString;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;
import com.lightbend.lagom.javadsl.api.transport.NotAcceptable;
import com.lightbend.lagom.javadsl.api.transport.UnsupportedMediaType;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;

/**
 * Message serializers.
 */
public class MessageSerializers {

    private MessageSerializers() {}

    /**
     * A serializer for {@link NotUsed} (empty) messages.
     */
    public static final StrictMessageSerializer<NotUsed> NOT_USED = new StrictMessageSerializer<NotUsed>() {
        @Override
        public NegotiatedSerializer<NotUsed, ByteString> serializerForRequest() {
            return aUnit -> ByteString.empty();
        }

        @Override
        public NegotiatedDeserializer<NotUsed, ByteString> deserializer(MessageProtocol messageProtocol) throws SerializationException {
            return bytes -> NotUsed.getInstance();
        }

        @Override
        public NegotiatedSerializer<NotUsed, ByteString> serializerForResponse(List<MessageProtocol> acceptedMessageProtocols) {
            return aUnit -> ByteString.empty();
        }

        @Override
        public boolean isUsed() {
            return false;
        }
    };

    /**
     * A serializer for plain text messages.
     */
    public static final StrictMessageSerializer<String> STRING = new StrictMessageSerializer<String>() {

        MessageProtocol defaultProtocol = new MessageProtocol(Optional.of("text/plain"), Optional.of("utf-8"), Optional.empty());

        class StringSerializer implements NegotiatedSerializer<String, ByteString> {
            private final MessageProtocol protocol;

            StringSerializer(MessageProtocol protocol) {
                this.protocol = protocol;
            }

            @Override
            public MessageProtocol protocol() {
                return protocol;
            }

            @Override
            public ByteString serialize(String s) throws SerializationException {
                return ByteString.fromString(s, protocol.charset().orElse("utf-8"));
            }
        }

        class StringDeserializer implements NegotiatedDeserializer<String, ByteString> {
            private final String charset;

            StringDeserializer(String charset) {
                this.charset = charset;
            }

            @Override
            public String deserialize(ByteString wire) throws DeserializationException {
                return wire.decodeString(charset);
            }
        }

        @Override
        public NegotiatedSerializer<String, ByteString> serializerForRequest() {
            return new StringSerializer(defaultProtocol);
        }

        @Override
        public NegotiatedDeserializer<String, ByteString> deserializer(MessageProtocol protocol) throws UnsupportedMediaType {
            if (protocol.contentType().orElse("text/plain").equals("text/plain")) {
                return new StringDeserializer(protocol.charset().orElse("utf-8"));
            } else {
                throw new UnsupportedMediaType(protocol, defaultProtocol);
            }
        }

        @Override
        public NegotiatedSerializer<String, ByteString> serializerForResponse(List<MessageProtocol> acceptedMessageProtocols) throws NotAcceptable {
            if (acceptedMessageProtocols.isEmpty()) {
                return serializerForRequest();
            } else {
                for (MessageProtocol messageProtocol: acceptedMessageProtocols) {
                    String contentType = messageProtocol.contentType().orElse("text/plain");

                    if (contentType.equals("text/plain") || contentType.equals("text/*") || contentType.equals("*/*") || contentType.equals("*")) {
                        return new StringSerializer(messageProtocol.withContentType("text/plain"));
                    }
                }
                throw new NotAcceptable(acceptedMessageProtocols, defaultProtocol);
            }
        }
    };
}
