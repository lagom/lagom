package com.lightbend.lagom.javadsl.api.deser;

import com.fasterxml.jackson.databind.JsonNode;

import java.lang.reflect.Type;

/**
 * A message schema.
 *
 * Message schemas are identified by {@link #messageType} and {@link #protocolId}. When Lagom outputs an OpenAPI
 * spec, it will put all message schemas in the definitions section of the spec. Since the same type might be used by
 * multiple service calls, the message serializers for those service calls will all output schemas for those types, and
 * so Lagom uses these to determine when two schemas are the same.
 *
 * When Lagom generates OpenAPI definitions, it tries to select the simplest name for a schema definition as possible,
 * starting with the simple name of the class. If two classes (in different packages) have the same name, then it will
 * attempt to generate a namespaced name with a common package name stripped.  If the same class has schemas defined
 * with two different protocols, Lagom will prefix the class name with the protocol name for each.
 */
public final class MessageSchema {

    private final Type messageType;
    private final String protocolId;
    private final JsonNode schema;

    public MessageSchema(Type messageType, String protocolId, JsonNode schema) {
        this.messageType = messageType;
        this.protocolId = protocolId;
        this.schema = schema;
    }

    /**
     * A message type.
     *
     * @return The type of the message.
     */
    public Type messageType() {
        return messageType;
    }

    /**
     * The id of the protocol used to handle this schema.
     *
     * Should be a simple String, preferably all lower case, for example "protobuf" or "json".
     *
     * @return The protocol ID.
     */
    public String protocolId() {
        return protocolId;
    }

    /**
     * The schema in JSON format, as specified by the OpenAPI Schema Object specification.
     *
     * OpenAPI is very JSON centric, however for non JSON formats, specification extensions, starting with x-, can be
     * used to specify something else.
     *
     * For non "json" protocols, Lagom will insert a "x-lagom-protocol" property with the protocol id for this schema,
     * in order to identify the protocol being used, such that when a client is generated from this spec, Lagom will
     * be able to identify which protocol to generate.
     *
     * @return The schema.
     * @see <https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#schemaObject>
     */
    public JsonNode schema() {
        return schema;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MessageSchema that = (MessageSchema) o;

        if (!messageType.equals(that.messageType)) return false;
        if (!protocolId.equals(that.protocolId)) return false;
        return schema.equals(that.schema);

    }

    @Override
    public int hashCode() {
        int result = messageType.hashCode();
        result = 31 * result + protocolId.hashCode();
        result = 31 * result + schema.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "MessageSchema{" +
                "messageType=" + messageType +
                ", protocolId='" + protocolId + '\'' +
                ", schema=" + schema +
                '}';
    }
}
