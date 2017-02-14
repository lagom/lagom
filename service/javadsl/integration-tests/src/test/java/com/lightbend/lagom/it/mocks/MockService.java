/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

import akka.util.ByteString;
import com.lightbend.lagom.javadsl.api.CircuitBreaker;

import akka.Done;
import akka.stream.javadsl.Source;
import akka.NotUsed;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.deser.SerializationException;
import com.lightbend.lagom.javadsl.api.deser.StrictMessageSerializer;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;
import com.lightbend.lagom.javadsl.api.transport.Method;
import com.lightbend.lagom.javadsl.api.transport.NotAcceptable;
import com.lightbend.lagom.javadsl.api.transport.UnsupportedMediaType;

import static com.lightbend.lagom.javadsl.api.Service.*;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public interface MockService extends Service {

    ServiceCall<MockRequestEntity, MockResponseEntity> mockCall(long id);

    ServiceCall<NotUsed, NotUsed> doNothing();
    
    ServiceCall<NotUsed, NotUsed> alwaysFail();
    
    ServiceCall<Done, Done> doneCall();

    ServiceCall<MockRequestEntity, Source<MockResponseEntity, ?>> streamResponse();

    ServiceCall<NotUsed, Source<MockResponseEntity, ?>> unitStreamResponse();

    ServiceCall<Source<MockRequestEntity, ?>, MockResponseEntity> streamRequest();

    ServiceCall<Source<MockRequestEntity, ?>, NotUsed> streamRequestUnit();

    ServiceCall<Source<MockRequestEntity, ?>, Source<MockResponseEntity, ?>> bidiStream();

    ServiceCall<String, String> customHeaders();

    ServiceCall<Source<String, ?>, Source<String, ?>> streamCustomHeaders();

    ServiceCall<NotUsed, String> serviceName();

    ServiceCall<NotUsed, Source<String, ?>> streamServiceName();

    ServiceCall<NotUsed, String> queryParamId(Optional<String> query);

    ServiceCall<MockRequestEntity, List<MockResponseEntity>> listResults();

    ServiceCall<MockRequestEntity, MockResponseEntity> customContentType();

    ServiceCall<MockRequestEntity, MockResponseEntity> noContentType();

    default Descriptor descriptor() {
        return named("mockservice").withCalls(
                restCall(Method.POST, "/mock/:id", this::mockCall),
                call(this::doNothing),
                call(this::alwaysFail).withCircuitBreaker(CircuitBreaker.identifiedBy("foo")),
                call(this::doneCall),
                call(this::streamResponse),
                call(this::unitStreamResponse),
                call(this::streamRequest),
                call(this::streamRequestUnit),
                call(this::bidiStream),
                call(this::customHeaders),
                call(this::streamCustomHeaders),
                call(this::serviceName),
                call(this::streamServiceName),
                pathCall("/queryparam?qp", this::queryParamId),
                call(this::listResults),
                call(this::customContentType)
                    .withRequestSerializer(new MockRequestEntitySerializer("application/mock-request-entity")),
                call(this::noContentType)
                    .withRequestSerializer(new MockRequestEntitySerializer())
        );
    }

    class MockRequestEntitySerializer implements StrictMessageSerializer<MockRequestEntity> {
        private final String contentType;

        MockRequestEntitySerializer(String contentType) {
            this.contentType = contentType;
        }

        MockRequestEntitySerializer() {
            this(null);
        }

        @Override
        public NegotiatedSerializer<MockRequestEntity, ByteString> serializerForRequest() {
            return new NegotiatedSerializer<MockRequestEntity, ByteString>() {
                @Override
                public ByteString serialize(MockRequestEntity mockRequestEntity) throws SerializationException {
                    return ByteString.fromString(mockRequestEntity.field2() + "\n" + mockRequestEntity.field1());
                }

                @Override
                public MessageProtocol protocol() {
                    MessageProtocol protocol = new MessageProtocol();
                    if (contentType != null) {
                        protocol = protocol.withContentType("application/mock-request-entity");
                    }
                    return protocol;
                }
            };
        }

        @Override
        public NegotiatedDeserializer<MockRequestEntity, ByteString> deserializer(MessageProtocol protocol) throws UnsupportedMediaType {
            if (!contentTypeSupported(protocol.contentType())) {
                throw new UnsupportedMediaType(protocol, new MessageProtocol().withContentType(contentType));
            }
            return string -> {
                try (Scanner scanner = new Scanner(string.utf8String()).useDelimiter("\n")) {
                    int field2 = scanner.nextInt();
                    String field1 = scanner.next();
                    return new MockRequestEntity(field1, field2);
                }
            };
        }

        private boolean contentTypeSupported(Optional<String> protocolContentType) {
            return (contentType == null) ||
                    (protocolContentType.isPresent() && protocolContentType.get().equals(contentType));
        }

        @Override
        public NegotiatedSerializer<MockRequestEntity, ByteString> serializerForResponse(List<MessageProtocol> acceptedMessageProtocols) throws NotAcceptable {
            return null;
        }
    }
}
