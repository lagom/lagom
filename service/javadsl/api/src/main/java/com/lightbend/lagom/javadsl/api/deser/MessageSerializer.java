/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.deser;

import akka.util.ByteString;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;
import com.lightbend.lagom.javadsl.api.transport.NotAcceptable;
import com.lightbend.lagom.javadsl.api.transport.UnsupportedMediaType;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import java.util.List;
import java.util.Optional;

/**
 * Serializer for messages.
 *
 * A message serializer is effectively a factory for negotiating serializers/deserializers.  They are created by passing
 * the relevant protocol information to then decide on a serializer/deserializer to use.
 *
 * The returned serializers/deserializers may be invoked once for strict messages, or many times for streamed messages.
 *
 * This interface doesn't actually specify the wireformat that the serializer must serialize to, there are two sub
 * interfaces that do, they being {@link StrictMessageSerializer}, which serializes messages that are primarily in
 * memory, to and from {@link ByteString}, and {@link StreamedMessageSerializer}, which serializes streams of messages.
 * Note that all message serializers used by the framework must implement one of these two sub interfaces, the
 * framework does not know how to handle other serializer types.
 *
 * @param <MessageEntity> The message entity being serialized/deserialized.
 */
public interface MessageSerializer<MessageEntity, WireFormat> {

    /**
     * The message headers that will be accepted for response serialization.
     */
    default PSequence<MessageProtocol> acceptResponseProtocols() {
        return TreePVector.empty();
    }

    /**
     * Whether this serializer serializes values that are used or not.
     *
     * If false, it means this serializer is for an empty request/response, eg, they use the
     * {@link akka.NotUsed} type.
     *
     * @return Whether the values this serializer serializes are used.
     */
    default boolean isUsed() {
        return true;
    }

    /**
     * Whether this serializer is a streamed serializer or not.
     *
     * @return Whether this is a streamed serializer.
     */
    default boolean isStreamed() { return false; }

    /**
     * Get a serializer for a client request.
     *
     * Since a client is the initiator of the request, it simply returns the default serializer for the entity.
     *
     * @return A serializer for request messages.
     */
    NegotiatedSerializer<MessageEntity, WireFormat> serializerForRequest();

    /**
     * Get a deserializer for an entity described by the given request or response protocol.
     *
     * @param protocol The protocol of the message request or response associated with teh entity.
     * @return A deserializer for request/response messages.
     * @throws UnsupportedMediaType If the deserializer can't deserialize that protocol.
     */
    NegotiatedDeserializer<MessageEntity, WireFormat> deserializer(MessageProtocol protocol) throws UnsupportedMediaType;

    /**
     * Negotiate a serializer for the response, given the accepted message headers.
     *
     * @param acceptedMessageProtocols The accepted message headers is a list of message headers that will be accepted by
     *                               the client. Any empty values in a message protocol, including the list itself,
     *                               indicate that any format is acceptable.
     * @throws NotAcceptable If the serializer can't meet the requirements of any of the accept headers.
     */
    NegotiatedSerializer<MessageEntity, WireFormat> serializerForResponse(List<MessageProtocol> acceptedMessageProtocols) throws NotAcceptable;

    /**
     * A negotiated serializer.
     *
     * @param <MessageEntity> The type of entity that this serializer serializes.
     * @param <WireFormat> The wire format that this serializer serializes to.
     */
    interface NegotiatedSerializer<MessageEntity, WireFormat> {

        /**
         * Get the protocol associated with this entity.
         */
        default MessageProtocol protocol() {
            return new MessageProtocol(Optional.empty(), Optional.empty(), Optional.empty());
        }

        /**
         * Serialize the given message entity.
         *
         * @param messageEntity The entity to serialize.
         * @return The serialized entity.
         */
        WireFormat serialize(MessageEntity messageEntity) throws SerializationException;
    }

    /**
     * A negotiated deserializer.
     *
     * @param <MessageEntity> The type of entity that this serializer serializes.
     * @param <WireFormat> The wire format that this serializer serializes to.
     */
    interface NegotiatedDeserializer<MessageEntity, WireFormat> {

        /**
         * Deserialize the given wire format.
         *
         * @param wire The raw wire data.
         * @return The deserialized entity.
         */
        MessageEntity deserialize(WireFormat wire) throws DeserializationException;
    }
}
