/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.security;

import com.lightbend.lagom.javadsl.api.transport.HeaderFilter;
import com.lightbend.lagom.javadsl.api.transport.RequestHeader;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;
import play.mvc.Http;

import java.security.Principal;
import java.util.Optional;

/**
 * Transfers service principal information via the <tt>User-Agent</tt> header.
 *
 * If using this on a service that serves requests from the outside world, it would be a good idea to block the
 * <tt>User-Agent</tt> header in the web facing load balancer/proxy.
 */
//#user-agent-auth-filter
public class UserAgentHeaderFilter implements HeaderFilter {

    @Override
    public RequestHeader transformClientRequest(RequestHeader request) {
        if (request.principal().isPresent()) {
            Principal principal = request.principal().get();
            if (principal instanceof ServicePrincipal) {
                String serviceName = ((ServicePrincipal) principal).serviceName();
                return request.withHeader(Http.HeaderNames.USER_AGENT, serviceName);
            } else {
                return request;
            }
        } else {
            return request;
        }
    }

    @Override
    public RequestHeader transformServerRequest(RequestHeader request) {
        Optional<String> userAgent = request.getHeader(Http.HeaderNames.USER_AGENT);
        if (userAgent.isPresent()) {
            return request.withPrincipal(ServicePrincipal.forServiceNamed(userAgent.get()));
        } else {
            return request;
        }
    }

    @Override
    public ResponseHeader transformServerResponse(ResponseHeader response, RequestHeader request) {
        return response;
    }

    @Override
    public ResponseHeader transformClientResponse(ResponseHeader response, RequestHeader request) {
        return response;
    }
}
//#user-agent-auth-filter
