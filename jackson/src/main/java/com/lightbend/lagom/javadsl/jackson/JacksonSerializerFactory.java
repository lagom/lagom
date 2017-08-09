/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.jackson;

import akka.Done;
import akka.actor.ActorSystem;
import akka.util.ByteString;
import akka.util.ByteString$;
import akka.util.ByteStringBuilder;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.lightbend.lagom.internal.jackson.JacksonObjectMapperProvider;
import com.lightbend.lagom.javadsl.api.deser.*;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * A Jackson Serializer Factory
 */
@Singleton
public class JacksonSerializerFactory implements SerializerFactory {

    private final ObjectMapper objectMapper;

    @Inject
    public JacksonSerializerFactory(ActorSystem system) {
        this(JacksonObjectMapperProvider.get(system).objectMapper());
    }

    /**
     * For testing purposes
     */
    public JacksonSerializerFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    @Override
    public <MessageEntity> StrictMessageSerializer<MessageEntity> messageSerializerFor(Type type) {
        if (type == Done.class)
          return new DoneMessageSerializer<>();
        else
          return new JacksonMessageSerializer<>(type);
    }

    private class JacksonMessageSerializer<MessageEntity> implements StrictMessageSerializer<MessageEntity> {

        private final MessageSerializer.NegotiatedSerializer<MessageEntity, ByteString> serializer;
        private final MessageSerializer.NegotiatedDeserializer<MessageEntity, ByteString> deserializer;

        public JacksonMessageSerializer(Type type) {
            JavaType javaType = objectMapper.constructType(type);
            serializer = new JacksonSerializer(objectMapper.writerFor(javaType));
            deserializer = new JacksonDeserializer(objectMapper.readerFor(javaType));
        }

        @Override
        public PSequence<MessageProtocol> acceptResponseProtocols() {
            return TreePVector.singleton(new MessageProtocol(Optional.of("application/json"), Optional.empty(),
                    Optional.empty()));
        }

        @Override
        public MessageSerializer.NegotiatedSerializer<MessageEntity, ByteString> serializerForRequest() {
            return serializer;
        }

        @Override
        public MessageSerializer.NegotiatedDeserializer<MessageEntity, ByteString> deserializer(MessageProtocol messageProtocol) throws SerializationException {
            return deserializer;
        }

        @Override
        public MessageSerializer.NegotiatedSerializer<MessageEntity, ByteString> serializerForResponse(java.util.List<MessageProtocol> acceptedMessageProtocols) {
            return serializer;
        }

        private class JacksonSerializer implements MessageSerializer.NegotiatedSerializer<MessageEntity, ByteString> {
            private final ObjectWriter writer;

            public JacksonSerializer(ObjectWriter writer) {
                this.writer = writer;
            }

            @Override
            public MessageProtocol protocol() {
                return new MessageProtocol(Optional.of("application/json"), Optional.of("utf-8"), Optional.empty());
            }

            @Override
            public ByteString serialize(MessageEntity messageEntity) {
                ByteStringBuilder builder = ByteString$.MODULE$.newBuilder();
                try {
                    writer.writeValue(builder.asOutputStream(), messageEntity);
                } catch (Exception e) {
                    throw new SerializationException(e);
                }
                return builder.result();
            }
        }

        private class JacksonDeserializer implements MessageSerializer.NegotiatedDeserializer<MessageEntity, ByteString> {
            private final ObjectReader reader;

            public JacksonDeserializer(ObjectReader reader) {
                this.reader = reader;
            }

            @Override
            public MessageEntity deserialize(ByteString bytes) {
                try {
                    return reader.readValue(bytes.iterator().asInputStream());
                } catch (Exception e) {
                    throw new DeserializationException(e);
                }
            }
        }
    }

  private class DoneMessageSerializer<MessageEntity> implements StrictMessageSerializer<MessageEntity> {

    private final MessageSerializer.NegotiatedSerializer<MessageEntity, ByteString> serializer = new DoneSerializer();
    private final MessageSerializer.NegotiatedDeserializer<MessageEntity, ByteString> deserializer = new DoneDeserializer();

    @Override
    public PSequence<MessageProtocol> acceptResponseProtocols() {
      return TreePVector.singleton(new MessageProtocol(Optional.of("application/json"), Optional.empty(), Optional
          .empty()));
    }

    @Override
    public MessageSerializer.NegotiatedSerializer<MessageEntity, ByteString> serializerForRequest() {
      return serializer;
    }

    @Override
    public MessageSerializer.NegotiatedDeserializer<MessageEntity, ByteString> deserializer(MessageProtocol messageProtocol)
        throws SerializationException {
      return deserializer;
    }

    @Override
    public MessageSerializer.NegotiatedSerializer<MessageEntity, ByteString> serializerForResponse(
        java.util.List<MessageProtocol> acceptedMessageProtocols) {
      return serializer;
    }

    private class DoneSerializer implements MessageSerializer.NegotiatedSerializer<MessageEntity, ByteString> {

      private final ByteString doneJson = ByteString.fromString("{ \"done\" : true }");

      @Override
      public MessageProtocol protocol() {
        return new MessageProtocol(Optional.of("application/json"), Optional.of("utf-8"), Optional.empty());
      }

      @Override
      public ByteString serialize(MessageEntity obj) {
        return doneJson;
      }
    }

    private class DoneDeserializer implements MessageSerializer.NegotiatedDeserializer<MessageEntity, ByteString> {
      @SuppressWarnings("unchecked")
      @Override
      public MessageEntity deserialize(ByteString bytes) {
        return (MessageEntity) Done.getInstance();
      }
    }
  }
}
