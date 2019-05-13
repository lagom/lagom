/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
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
import com.lightbend.lagom.javadsl.api.deser.DeserializationException;
import com.lightbend.lagom.javadsl.api.deser.SerializationException;
import com.lightbend.lagom.javadsl.api.deser.SerializerFactory;
import com.lightbend.lagom.javadsl.api.deser.StrictMessageSerializer;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

/** A Jackson Serializer Factory */
@Singleton
public class JacksonSerializerFactory implements SerializerFactory {

  private final MessageProtocol defaultProtocol =
      new MessageProtocol(Optional.of("application/json"), Optional.of("utf-8"), Optional.empty());

  private final ObjectMapper objectMapper;

  @Inject
  public JacksonSerializerFactory(ActorSystem system) {
    this(JacksonObjectMapperProvider.get(system).objectMapper());
  }

  /** For testing purposes */
  public JacksonSerializerFactory(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public <MessageEntity> StrictMessageSerializer<MessageEntity> messageSerializerFor(Type type) {
    if (type == Done.class) return new DoneMessageSerializer<>();
    else if (type == ByteString.class) return new NoopMessageSerializer<>();
    else return new JacksonMessageSerializer<>(type);
  }

  private class JacksonMessageSerializer<MessageEntity>
      implements StrictMessageSerializer<MessageEntity> {

    private final NegotiatedSerializer<MessageEntity, ByteString> serializer;
    private final NegotiatedDeserializer<MessageEntity, ByteString> deserializer;

    public JacksonMessageSerializer(Type type) {
      JavaType javaType = objectMapper.constructType(type);
      serializer = new JacksonSerializer(objectMapper.writerFor(javaType));
      deserializer = new JacksonDeserializer(objectMapper.readerFor(javaType), type);
    }

    @Override
    public PSequence<MessageProtocol> acceptResponseProtocols() {
      return TreePVector.singleton(
          new MessageProtocol(Optional.of("application/json"), Optional.empty(), Optional.empty()));
    }

    @Override
    public NegotiatedSerializer<MessageEntity, ByteString> serializerForRequest() {
      return serializer;
    }

    @Override
    public NegotiatedDeserializer<MessageEntity, ByteString> deserializer(
        MessageProtocol messageProtocol) throws SerializationException {
      return deserializer;
    }

    @Override
    public NegotiatedSerializer<MessageEntity, ByteString> serializerForResponse(
        List<MessageProtocol> acceptedMessageProtocols) {
      return serializer;
    }

    private class JacksonSerializer implements NegotiatedSerializer<MessageEntity, ByteString> {
      private final ObjectWriter writer;

      public JacksonSerializer(ObjectWriter writer) {
        this.writer = writer;
      }

      @Override
      public MessageProtocol protocol() {
        return defaultProtocol;
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

    private class JacksonDeserializer implements NegotiatedDeserializer<MessageEntity, ByteString> {
      private final ObjectReader reader;
      private final Type type;

      public JacksonDeserializer(ObjectReader reader, Type type) {
        this.reader = reader;
        this.type = type;
      }

      @Override
      public MessageEntity deserialize(ByteString bytes) {
        try {
          if (bytes.isEmpty() && this.type == Optional.class) {
            bytes = ByteString.fromString("null");
          }
          return reader.readValue(bytes.iterator().asInputStream());
        } catch (Exception e) {
          throw new DeserializationException(e);
        }
      }
    }
  }

  private class DoneMessageSerializer<MessageEntity>
      implements StrictMessageSerializer<MessageEntity> {

    private final NegotiatedSerializer<MessageEntity, ByteString> serializer = new DoneSerializer();
    private final NegotiatedDeserializer<MessageEntity, ByteString> deserializer =
        new DoneDeserializer();

    @Override
    public PSequence<MessageProtocol> acceptResponseProtocols() {
      return TreePVector.singleton(
          new MessageProtocol(Optional.of("application/json"), Optional.empty(), Optional.empty()));
    }

    @Override
    public NegotiatedSerializer<MessageEntity, ByteString> serializerForRequest() {
      return serializer;
    }

    @Override
    public NegotiatedDeserializer<MessageEntity, ByteString> deserializer(
        MessageProtocol messageProtocol) throws SerializationException {
      return deserializer;
    }

    @Override
    public NegotiatedSerializer<MessageEntity, ByteString> serializerForResponse(
        List<MessageProtocol> acceptedMessageProtocols) {
      return serializer;
    }

    private class DoneSerializer implements NegotiatedSerializer<MessageEntity, ByteString> {

      private final ByteString doneJson = ByteString.fromString("{ \"done\" : true }");

      @Override
      public MessageProtocol protocol() {
        return defaultProtocol;
      }

      @Override
      public ByteString serialize(MessageEntity obj) {
        return doneJson;
      }
    }

    private class DoneDeserializer implements NegotiatedDeserializer<MessageEntity, ByteString> {
      @SuppressWarnings("unchecked")
      @Override
      public MessageEntity deserialize(ByteString bytes) {
        return (MessageEntity) Done.getInstance();
      }
    }
  }

  private class NoopMessageSerializer<MessageEntity>
      implements StrictMessageSerializer<MessageEntity> {

    private final NegotiatedSerializer<MessageEntity, ByteString> serializer = new NoopSerializer();
    private final NegotiatedDeserializer<MessageEntity, ByteString> deserializer =
        new NoopDeserializer();

    private class NoopSerializer implements NegotiatedSerializer<MessageEntity, ByteString> {

      @Override
      public MessageProtocol protocol() {
        return defaultProtocol;
      }

      @Override
      public ByteString serialize(MessageEntity obj) {
        return (ByteString) obj;
      }
    }

    private class NoopDeserializer implements NegotiatedDeserializer<MessageEntity, ByteString> {
      @Override
      @SuppressWarnings("unchecked")
      public MessageEntity deserialize(ByteString bytes) {
        return (MessageEntity) bytes;
      }
    }

    @Override
    public NegotiatedSerializer<MessageEntity, ByteString> serializerForRequest() {
      return serializer;
    }

    @Override
    public NegotiatedDeserializer<MessageEntity, ByteString> deserializer(
        MessageProtocol messageProtocol) throws SerializationException {
      return deserializer;
    }

    @Override
    public NegotiatedSerializer<MessageEntity, ByteString> serializerForResponse(
        List<MessageProtocol> acceptedMessageProtocols) {
      return serializer;
    }
  }
}
