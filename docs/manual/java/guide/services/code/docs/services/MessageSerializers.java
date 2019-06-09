/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services;

import akka.NotUsed;
import akka.util.ByteString;
import akka.util.ByteStringBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.deser.*;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;
import com.lightbend.lagom.javadsl.api.transport.NotAcceptable;
import com.lightbend.lagom.javadsl.api.transport.UnsupportedMediaType;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import static com.lightbend.lagom.javadsl.api.Service.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class MessageSerializers {

  public class Order {}

  public static class MyOrderSerializer implements StrictMessageSerializer<Order> {
    @Override
    public NegotiatedSerializer<Order, ByteString> serializerForRequest() {
      return null;
    }

    @Override
    public NegotiatedDeserializer<Order, ByteString> deserializer(MessageProtocol protocol)
        throws UnsupportedMediaType {
      return null;
    }

    @Override
    public NegotiatedSerializer<Order, ByteString> serializerForResponse(
        List acceptedMessageProtocols) throws NotAcceptable {
      return null;
    }
  }

  public interface OrderService1 extends Service {
    ServiceCall<NotUsed, Order> getOrder(String id);

    @Override
    // #call-serializer
    default Descriptor descriptor() {
      return named("orderservice")
          .withCalls(
              pathCall("/orders/:id", this::getOrder)
                  .withResponseSerializer(new MyOrderSerializer()));
    }
    // #call-serializer
  }

  public interface OrderService2 extends Service {
    ServiceCall<NotUsed, Order> getOrder(String id);

    @Override
    // #type-serializer
    default Descriptor descriptor() {
      return named("orderservice")
          .withCalls(pathCall("/orders/:id", this::getOrder))
          .withMessageSerializer(Order.class, new MyOrderSerializer());
    }
    // #type-serializer
  }

  public static class MySerializerFactory implements SerializerFactory {
    @Override
    public <MessageEntity> MessageSerializer<MessageEntity, ?> messageSerializerFor(Type type) {
      return null;
    }
  }

  public interface OrderService3 extends Service {
    ServiceCall<NotUsed, Order> getOrder(String id);

    @Override
    // #with-serializer-factory
    default Descriptor descriptor() {
      return named("orderservice")
          .withCalls(pathCall("/orders/:id", this::getOrder))
          .withSerializerFactory(new MySerializerFactory());
    }
    // #with-serializer-factory
  }

  // Using an interface means we don't need to make the inner classes static, which looks better in
  // the docs
  public interface CustomString {

    // #plain-text-serializer
    public class PlainTextSerializer
        implements MessageSerializer.NegotiatedSerializer<String, ByteString> {
      private final String charset;

      public PlainTextSerializer(String charset) {
        this.charset = charset;
      }

      @Override
      public MessageProtocol protocol() {
        return new MessageProtocol(
            Optional.of("text/plain"), Optional.of(charset), Optional.empty());
      }

      @Override
      public ByteString serialize(String s) throws SerializationException {
        return ByteString.fromString(s, charset);
      }
    }
    // #plain-text-serializer

    // #json-text-serializer
    public class JsonTextSerializer
        implements MessageSerializer.NegotiatedSerializer<String, ByteString> {
      private final ObjectMapper mapper = new ObjectMapper();

      @Override
      public MessageProtocol protocol() {
        return new MessageProtocol(
            Optional.of("application/json"), Optional.empty(), Optional.empty());
      }

      @Override
      public ByteString serialize(String s) throws SerializationException {
        try {
          return ByteString.fromArray(mapper.writeValueAsBytes(s));
        } catch (JsonProcessingException e) {
          throw new SerializationException(e);
        }
      }
    }
    // #json-text-serializer

    // #plain-text-deserializer
    public class PlainTextDeserializer
        implements MessageSerializer.NegotiatedDeserializer<String, ByteString> {
      private final String charset;

      public PlainTextDeserializer(String charset) {
        this.charset = charset;
      }

      @Override
      public String deserialize(ByteString bytes) throws DeserializationException {
        return bytes.decodeString(charset);
      }
    }
    // #plain-text-deserializer

    // #json-text-deserializer
    public class JsonTextDeserializer
        implements MessageSerializer.NegotiatedDeserializer<String, ByteString> {
      private final ObjectMapper mapper = new ObjectMapper();

      @Override
      public String deserialize(ByteString bytes) throws DeserializationException {
        try {
          return mapper.readValue(bytes.iterator().asInputStream(), String.class);
        } catch (IOException e) {
          throw new DeserializationException(e);
        }
      }
    }
    // #json-text-deserializer

    // #text-serializer
    public class TextMessageSerializer implements StrictMessageSerializer<String> {
      // #text-serializer

      // #text-serializer-protocols
      @Override
      public PSequence<MessageProtocol> acceptResponseProtocols() {
        return TreePVector.from(
            Arrays.asList(
                new MessageProtocol().withContentType("text/plain"),
                new MessageProtocol().withContentType("application/json")));
      }
      // #text-serializer-protocols

      // #text-serializer-request
      @Override
      public NegotiatedSerializer<String, ByteString> serializerForRequest() {
        return new PlainTextSerializer("utf-8");
      }
      // #text-serializer-request

      // #text-deserializer
      @Override
      public NegotiatedDeserializer<String, ByteString> deserializer(MessageProtocol protocol)
          throws UnsupportedMediaType {
        if (protocol.contentType().isPresent()) {
          if (protocol.contentType().get().equals("text/plain")) {
            return new PlainTextDeserializer(protocol.charset().orElse("utf-8"));
          } else if (protocol.contentType().get().equals("application/json")) {
            return new JsonTextDeserializer();
          } else {
            throw new UnsupportedMediaType(
                protocol, new MessageProtocol().withContentType("text/plain"));
          }
        } else {
          return new PlainTextDeserializer("utf-8");
        }
      }
      // #text-deserializer

      // #text-serializer-response
      @Override
      public NegotiatedSerializer<String, ByteString> serializerForResponse(
          List<MessageProtocol> acceptedMessageProtocols) throws NotAcceptable {
        if (acceptedMessageProtocols.isEmpty()) {
          return new PlainTextSerializer("utf-8");
        } else {
          for (MessageProtocol protocol : acceptedMessageProtocols) {
            if (protocol.contentType().isPresent()) {
              String contentType = protocol.contentType().get();
              if (contentType.equals("text/plain")
                  || contentType.equals("text/*")
                  || contentType.equals("*/*")) {
                return new PlainTextSerializer(protocol.charset().orElse("utf-8"));
              } else if (protocol.contentType().get().equals("application/json")) {
                return new JsonTextSerializer();
              }
            } else {
              return new PlainTextSerializer(protocol.charset().orElse("utf-8"));
            }
          }
          throw new NotAcceptable(
              acceptedMessageProtocols, new MessageProtocol().withContentType("text/plain"));
        }
      }
      // #text-serializer-response

    }
  }

  interface Protobufs {

    // Not real protobuf generated class...
    public class Order {
      public void writeTo(OutputStream os) {}

      public static Order parseFrom(InputStream is) {
        return null;
      }
    }

    // #protobuf
    public class ProtobufSerializer implements StrictMessageSerializer<Order> {
      private final NegotiatedSerializer<Order, ByteString> serializer =
          new NegotiatedSerializer<Order, ByteString>() {
            @Override
            public MessageProtocol protocol() {
              return new MessageProtocol().withContentType("application/octet-stream");
            }

            @Override
            public ByteString serialize(Order order) throws SerializationException {
              ByteStringBuilder builder = ByteString.createBuilder();
              order.writeTo(builder.asOutputStream());
              return builder.result();
            }
          };
      private final NegotiatedDeserializer<Order, ByteString> deserializer =
          bytes -> Order.parseFrom(bytes.iterator().asInputStream());

      @Override
      public NegotiatedSerializer<Order, ByteString> serializerForRequest() {
        return serializer;
      }

      @Override
      public NegotiatedDeserializer<Order, ByteString> deserializer(MessageProtocol protocol)
          throws UnsupportedMediaType {
        return deserializer;
      }

      @Override
      public NegotiatedSerializer<Order, ByteString> serializerForResponse(
          List<MessageProtocol> acceptedMessageProtocols) throws NotAcceptable {
        return serializer;
      }
    }
    // #protobuf
  }

  interface JAXB {

    // #jaxb
    public class JaxbSerializerFactory implements SerializerFactory {
      private final Unmarshaller unmarshaller;
      private final Marshaller marshaller;

      public JaxbSerializerFactory() {
        try {
          JAXBContext context = JAXBContext.newInstance();
          this.unmarshaller = context.createUnmarshaller();
          this.marshaller = context.createMarshaller();
        } catch (JAXBException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      @SuppressWarnings("unchecked")
      public <MessageEntity> MessageSerializer<MessageEntity, ?> messageSerializerFor(Type type) {
        if (type instanceof Class) {
          Class<MessageEntity> clazz = (Class<MessageEntity>) type;

          return new StrictMessageSerializer<MessageEntity>() {

            NegotiatedSerializer<MessageEntity, ByteString> serializer =
                new NegotiatedSerializer<MessageEntity, ByteString>() {
                  @Override
                  public MessageProtocol protocol() {
                    return new MessageProtocol().withContentType("application/xml");
                  }

                  @Override
                  public ByteString serialize(MessageEntity messageEntity)
                      throws SerializationException {
                    ByteStringBuilder builder = ByteString.createBuilder();
                    try {
                      marshaller.marshal(messageEntity, builder.asOutputStream());
                      return builder.result();
                    } catch (JAXBException e) {
                      throw new SerializationException(e);
                    }
                  }
                };

            NegotiatedDeserializer<MessageEntity, ByteString> deserializer =
                bytes -> {
                  try {
                    return unmarshaller
                        .unmarshal(new StreamSource(bytes.iterator().asInputStream()), clazz)
                        .getValue();
                  } catch (JAXBException e) {
                    throw new DeserializationException(e);
                  }
                };

            @Override
            public NegotiatedSerializer<MessageEntity, ByteString> serializerForRequest() {
              return serializer;
            }

            @Override
            public NegotiatedDeserializer<MessageEntity, ByteString> deserializer(
                MessageProtocol protocol) throws UnsupportedMediaType {
              return deserializer;
            }

            @Override
            public NegotiatedSerializer<MessageEntity, ByteString> serializerForResponse(
                List<MessageProtocol> acceptedMessageProtocols) throws NotAcceptable {
              return serializer;
            }
          };
        } else {
          throw new IllegalArgumentException("JAXB does not support deserializing generic types");
        }
      }
    }
    // #jaxb
  }
}
