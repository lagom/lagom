/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.transport;

import java.io.Serializable;
import java.util.*;

/**
 * An error code that gets translated into an appropriate underlying error code.
 *
 * This attempts to match up corresponding HTTP error codes with WebSocket close codes, so that user code can
 * generically select a code without worrying about the underlying transport.
 *
 * While most WebSocket close codes that we typically use do have a corresponding HTTP error code, there are many
 * HTTP error codes that don't have a corresponding WebSocket close code.  In these cases, we use the private WebSocket
 * close code range (4xxx), with the HTTP error code as the last three digits.  Such WebSocket close codes will be in
 * the range 4400 to 4599.
 *
 * This class should only be used to represent error codes, status codes like HTTP 200 should not be represented using
 * this class.  This is enforced for HTTP codes, since they have a well defined categorisation, codes between 400 and
 * 599 are considered errors.  It is however not enforced for WebSockets, since the WebSocket protocol defines no such
 * categorisation of codes, it specifies a number of well known codes from 1000 to 1015, with no particular pattern to
 * their meaning, and the remaining codes are only categorised by whether they are private, reserved for the WebSocket
 * spec, or reserved for applications to specify.
 *
 * For WebSocket close codes that are not known, or are not in the private range of 4400 to 4599 defined by us, this use
 * class uses the generic HTTP 404 error code.
 */
public final class TransportErrorCode implements Serializable {

    /**
     * A protocol error, or bad request.
     */
    public static final TransportErrorCode ProtocolError = new TransportErrorCode(400, 1002, "Protocol Error/Bad Request");

    /**
     * An application level protocol error, such as when a client or server sent data that can't be deserialized.
     */
    public static final TransportErrorCode UnsupportedData = new TransportErrorCode(400, 1003, "Unsupported Data/Bad Request");

    /**
     * A bad request, most often this will be equivalent to unsupported data.
     */
    public static final TransportErrorCode BadRequest = UnsupportedData;

    /**
     * A particular operation was forbidden.
     */
    public static final TransportErrorCode Forbidden = new TransportErrorCode(403, 4403, "Forbidden");

    /**
     * A generic error to used to indicate that the end receiving the error message violated the remote ends policy.
     */
    public static final TransportErrorCode PolicyViolation = new TransportErrorCode(404, 1008, "Policy Violation");

    /**
     * A resource was not found, equivalent to policy violation.
     */
    public static final TransportErrorCode NotFound = PolicyViolation;

    /**
     * The method being used is not allowed.
     */
    public static final TransportErrorCode MethodNotAllowed = new TransportErrorCode(405, 4405, "Method Not Allowed");

    /**
     * The server can't generate a response that meets the clients accepted response types.
     */
    public static final TransportErrorCode NotAcceptable = new TransportErrorCode(406, 4406, "Not Acceptable");

    /**
     * The payload of a message is too large.
     */
    public static final TransportErrorCode PayloadTooLarge = new TransportErrorCode(413, 1009, "Payload Too Large");

    /**
     * The client or server doesn't know how to deserialize the request or response.
     */
    public static final TransportErrorCode UnsupportedMediaType = new TransportErrorCode(415, 4415, "Unsupported Media Type");


    /**
     * A generic error used to indicate that the end sending the error message because it encountered an unexpected
     * condition.
     */
    public static final TransportErrorCode UnexpectedCondition = new TransportErrorCode(500, 1011, "Unexpected Condition");

    /**
     * An internal server error, equivalent to Unexpected Condition.
     */
    public static final TransportErrorCode InternalServerError = UnexpectedCondition;

    /**
     * Service unavailable, thrown when the service is unavailable or going away.
     */
    public static final TransportErrorCode ServiceUnavailable = new TransportErrorCode(503, 1001, "Going Away/Service Unavailable");

    /**
     * Going away, thrown when the service is unavailable or going away.
     */
    public static final TransportErrorCode GoingAway = ServiceUnavailable;

    private static final Map<Integer, TransportErrorCode> HTTP_ERROR_CODE_MAP;
    private static final Map<Integer, TransportErrorCode> WEBSOCKET_ERROR_CODE_MAP;

    static {
        List<TransportErrorCode> allErrorCodes = Arrays.asList(
                ProtocolError,
                UnsupportedData,
                Forbidden,
                PolicyViolation,
                MethodNotAllowed,
                NotAcceptable,
                PayloadTooLarge,
                UnsupportedMediaType,
                UnexpectedCondition,
                ServiceUnavailable
        );
        Map<Integer, TransportErrorCode> http = new HashMap<>();
        allErrorCodes.forEach(code -> http.put(code.http, code));
        HTTP_ERROR_CODE_MAP = Collections.unmodifiableMap(http);
        Map<Integer, TransportErrorCode> websocket = new HashMap<>();
        allErrorCodes.forEach(code -> websocket.put(code.webSocket, code));
        WEBSOCKET_ERROR_CODE_MAP = Collections.unmodifiableMap(websocket);
    }

    /**
     * Get a transport error code from the given HTTP error code.
     *
     * @param code The HTTP error code, must be between 400 and 599 inclusive.
     * @return The transport error code.
     * @throws IllegalArgumentException if the HTTP code was not between 400 and 599.
     */
    public static TransportErrorCode fromHttp(int code) {
        TransportErrorCode builtIn = HTTP_ERROR_CODE_MAP.get(code);
        if (builtIn == null) {
            if (code > 599 || code < 100) {
                throw new IllegalArgumentException("Invalid http status code: " + code);
            } else if (code < 400) {
                throw new IllegalArgumentException("Invalid http error code: " + code);
            } else {
                return new TransportErrorCode(code, 4000 + code, "Unknown error code");
            }
        } else {
            return builtIn;
        }
    }

    /**
     * Get a transport error code from the given WebSocket close code.
     *
     * @param code The WebSocket close code, must be between 0 and 65535 inclusive.
     * @return The transport error code.
     * @throws IllegalArgumentException if the code is not an unsigned 2 byte integer.
     */
    public static TransportErrorCode fromWebSocket(int code) {
        TransportErrorCode builtIn = WEBSOCKET_ERROR_CODE_MAP.get(code);
        if (builtIn == null) {
            if (code < 0 || code > 65535) {
                throw new IllegalArgumentException("Invalid WebSocket status code: " + code);
            } else if (code >= 4400 && code <= 4599) {
                return new TransportErrorCode(code - 4000, code, "Unknown error code");
            } else {
                return new TransportErrorCode(404, code, "Unknown error code");
            }
        } else {
            return builtIn;
        }
    }

    private final int http;
    private final int webSocket;
    private final String description;

    private TransportErrorCode(int http, int webSocket, String description) {
        this.http = http;
        this.webSocket = webSocket;
        this.description = description;
    }

    /**
     * The HTTP status code for this error.
     *
     * @return A value between 400 and 599.
     */
    public int http() {
        return http;
    }

    /**
     * The WebSocket close code for this error.
     *
     * @return A value from 0 to 65535.
     */
    public int webSocket() {
        return webSocket;
    }

    /**
     * A description of this close code.
     *
     * This description will be meaningful for known built in close codes, but for other codes, it will be
     * {@code "Unknown error code"}.
     *
     * @return A description of this closed code.
     */
    public String description() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransportErrorCode that = (TransportErrorCode) o;

        if (http != that.http) return false;
        return webSocket == that.webSocket;

    }

    @Override
    public int hashCode() {
        int result = http;
        result = 31 * result + webSocket;
        return result;
    }

    @Override
    public String toString() {
        return "TransportErrorCode{" +
                "http=" + http +
                ", webSocket=" + webSocket +
                ", description='" + description + '\'' +
                '}';
    }
}
