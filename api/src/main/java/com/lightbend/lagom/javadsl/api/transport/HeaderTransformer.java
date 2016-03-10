/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.transport;

/**
 * Transformer of headers.
 *
 * This is used to transform transport and protocol headers according to various strategies for protocol and version
 * negotiation, as well as authentication.
 */
public interface HeaderTransformer {

    /**
     * A noop header transformer, used to deconfigure specific transformers.
     */
    HeaderTransformer NONE = new HeaderTransformer() {
        @Override
        public RequestHeader transformClientRequest(RequestHeader request) {
            return request;
        }
        @Override
        public RequestHeader transformServerRequest(RequestHeader request) {
            return request;
        }
        @Override
        public ResponseHeader transformServerResponse(ResponseHeader response, RequestHeader request) {
            return response;
        }
        @Override
        public ResponseHeader transformClientResponse(ResponseHeader response, RequestHeader request) {
            return response;
        }
    };

    /**
     * Transform the given client request.
     *
     * This will be invoked on all requests outgoing from the client.
     *
     * @param request The client request header.
     * @return The transformed client request header.
     */
    RequestHeader transformClientRequest(RequestHeader request);

    /**
     * Transform the given server request.
     *
     * This will be invoked on all requests incoming to the server.
     *
     * @param request The server request header.
     * @return The transformed server request header.
     */
    RequestHeader transformServerRequest(RequestHeader request);

    /**
     * Transform the given server response.
     *
     * This will be invoked on all responses outgoing from the server.
     *
     * @param response The server response.
     * @param request The transformed server request. Useful for when the response transformation requires information
     *                from the client.
     * @return The transformed server response header.
     */
    ResponseHeader transformServerResponse(ResponseHeader response, RequestHeader request);

    /**
     * Transform the given client response.
     *
     * This will be invoked on all responses incoming to the client.
     *
     * @param response The client response.
     * @param request The client request. Useful for when the response transformation requires information from the
     *                client request.
     * @return The transformed client response header.
     */
    ResponseHeader transformClientResponse(ResponseHeader response, RequestHeader request);
}
