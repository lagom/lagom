/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.transport;

import com.google.common.collect.Lists;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import java.util.Arrays;

/**
 * Filter for headers.
 *
 * This is used to transform transport and protocol headers according to various strategies for protocol and version
 * negotiation, as well as authentication.
 */
public interface HeaderFilter {

    /**
     * A noop header transformer, used to deconfigure specific transformers.
     */
    HeaderFilter NONE = new HeaderFilter() {
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

    /**
     * Create a composite header filter from multiple header filters.
     *
     * The order that the filters are applied are forward for headers being sent over the wire, and in reverse for
     * headers that are received from the wire.
     *
     * @param filters The filters to create the composite filter from.
     * @return The composite filter.
     */
    static HeaderFilter composite(HeaderFilter... filters) {
        return new Composite(TreePVector.from(Arrays.asList(filters)));
    }

    /**
     * A composite header filter.
     *
     * The order that the filters are applied are forward for headers being sent over the wire, and in reverse for
     * headers that are received from the wire.
     */
    class Composite implements HeaderFilter {

        private final PSequence<HeaderFilter> headerFilters;

        public Composite(PSequence<HeaderFilter> headerFilters) {
            PSequence<HeaderFilter> filters = TreePVector.empty();
            for (HeaderFilter headerFilter: headerFilters) {
                if (headerFilter instanceof Composite) {
                    filters = filters.plusAll(((Composite) headerFilter).headerFilters);
                } else {
                    filters = filters.plus(headerFilter);
                }
            }
            this.headerFilters = filters;
        }

        @Override
        public RequestHeader transformClientRequest(RequestHeader request) {
            for (HeaderFilter filter: headerFilters) {
                request = filter.transformClientRequest(request);
            }
            return request;
        }

        @Override
        public RequestHeader transformServerRequest(RequestHeader request) {
            for (HeaderFilter filter: Lists.reverse(headerFilters)) {
                request = filter.transformServerRequest(request);
            }
            return request;
        }

        @Override
        public ResponseHeader transformServerResponse(ResponseHeader response, RequestHeader request) {
            for (HeaderFilter filter: headerFilters) {
                response = filter.transformServerResponse(response, request);
            }
            return response;
        }

        @Override
        public ResponseHeader transformClientResponse(ResponseHeader response, RequestHeader request) {
            for (HeaderFilter filter: Lists.reverse(headerFilters)) {
                response = filter.transformClientResponse(response, request);
            }
            return response;
        }
    }
}
