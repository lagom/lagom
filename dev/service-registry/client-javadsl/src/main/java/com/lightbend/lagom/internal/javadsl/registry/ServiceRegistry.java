/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.registry;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.pathCall;
import static com.lightbend.lagom.javadsl.api.Service.restCall;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.pcollections.PSequence;

import akka.NotUsed;
import akka.util.ByteString;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.deser.DeserializationException;
import com.lightbend.lagom.javadsl.api.deser.SerializationException;
import com.lightbend.lagom.javadsl.api.deser.StrictMessageSerializer;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;
import com.lightbend.lagom.javadsl.api.transport.Method;
import com.lightbend.lagom.javadsl.api.transport.NotAcceptable;
import com.lightbend.lagom.javadsl.api.transport.UnsupportedMediaType;

public interface ServiceRegistry extends Service {

	String SERVICE_NAME = "lagom-service-registry";

	ServiceCall<ServiceRegistryService, NotUsed> register(String name);
	ServiceCall<NotUsed, NotUsed> unregister(String name);
	ServiceCall<NotUsed, URI> lookup(String name);
	ServiceCall<NotUsed, PSequence<RegisteredService>> registeredServices();
	
	@Override
	default Descriptor descriptor() {
		// @formatter:off
		return named(SERVICE_NAME).withCalls(
            restCall(Method.PUT, "/services/:id", this::register),
		    restCall(Method.DELETE, "/services/:id", this::unregister),
		    restCall(Method.GET, "/services/:id", this::lookup).withResponseSerializer(CustomSerializers.URI),
		    pathCall("/services", this::registeredServices)
        ).withLocatableService(false);
		// @formatter:on
	}
}

class CustomSerializers {
  /**
   * A serializer for URI.
   */
  public static final StrictMessageSerializer<URI> URI = new StrictMessageSerializer<URI>() {

      MessageProtocol defaultProtocol = new MessageProtocol(Optional.of("text/plain"), Optional.of("utf-8"), Optional.empty());

      class URISerializer implements NegotiatedSerializer<URI, ByteString> {
          private final MessageProtocol protocol;

          URISerializer(MessageProtocol protocol) {
              this.protocol = protocol;
          }

          @Override
          public MessageProtocol protocol() {
              return protocol;
          }

          @Override
          public ByteString serialize(URI uri) throws SerializationException {
              return ByteString.fromString(uri.toString(), protocol.charset().orElse("utf-8"));
          }
      }

      class URIDeserializer implements NegotiatedDeserializer<URI, ByteString> {
          private final String charset;

          URIDeserializer(String charset) {
              this.charset = charset;
          }

          @Override
          public URI deserialize(ByteString wire) throws DeserializationException {
              try {
                return new URI(wire.decodeString(charset));
              }
              catch(URISyntaxException e) {
                throw new DeserializationException(e);
              } 
          }
      }

      @Override
      public NegotiatedSerializer<URI, ByteString> serializerForRequest() {
          return new URISerializer(defaultProtocol);
      }

      @Override
      public NegotiatedDeserializer<URI, ByteString> deserializer(MessageProtocol protocol) throws UnsupportedMediaType {
          if (protocol.contentType().orElse("text/plain").equals("text/plain")) {
              return new URIDeserializer(protocol.charset().orElse("utf-8"));
          } else {
              throw new UnsupportedMediaType(protocol, defaultProtocol);
          }
      }

      @Override
      public NegotiatedSerializer<URI, ByteString> serializerForResponse(List<MessageProtocol> acceptedMessageProtocols) throws NotAcceptable {
          if (acceptedMessageProtocols.isEmpty()) {
              return serializerForRequest();
          } else {
              for (MessageProtocol messageProtocol: acceptedMessageProtocols) {
                  String contentType = messageProtocol.contentType().orElse("text/plain");

                  if (contentType.equals("text/plain") || contentType.equals("text/*") || contentType.equals("*/*") || contentType.equals("*")) {
                      return new URISerializer(messageProtocol.withContentType("text/plain"));
                  }
              }
              throw new NotAcceptable(acceptedMessageProtocols, defaultProtocol);
          }
      }
  };
}
