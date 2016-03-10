/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.transport;

import com.lightbend.lagom.javadsl.api.transport.HeaderTransformer;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;
import com.lightbend.lagom.javadsl.api.transport.RequestHeader;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Negotiates the protocol using versions from the path.
 */
public class PathVersionedProtocolNegotiationStrategy implements HeaderTransformer {

    private final Pattern pathVersionExtractor;
    private final String pathVersionFormat;

    public PathVersionedProtocolNegotiationStrategy(Pattern pathVersionExtractor, String pathVersionFormat) {
        this.pathVersionExtractor = pathVersionExtractor;
        this.pathVersionFormat = pathVersionFormat;
    }

    public PathVersionedProtocolNegotiationStrategy() {
        this(Pattern.compile("/(^/+)(/.*)"), "/%s%s");
    }

    @Override
    public ResponseHeader transformServerResponse(ResponseHeader response, RequestHeader request) {
        return response;
    }

    @Override
    public ResponseHeader transformClientResponse(ResponseHeader response, RequestHeader request) {
        Optional<String> requestVersion = request.protocol().version();
        if (requestVersion.isPresent()) {
            return response.withProtocol(response.protocol().withVersion(requestVersion.get()));
        } else {
            return response;
        }
    }

    @Override
    public RequestHeader transformServerRequest(RequestHeader request) {
        URI uri = request.uri();
        Matcher matcher = pathVersionExtractor.matcher(uri.getPath());

        if (matcher.matches()) {
            String version = matcher.group(1);
            String remainder = matcher.group(2);
            try {
                URI newUri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), remainder,
                        uri.getQuery(), uri.getFragment());

                PSequence<MessageProtocol> acceptedResponseProtocols;
                if (request.acceptedResponseProtocols().isEmpty()) {
                    acceptedResponseProtocols = TreePVector.singleton(new MessageProtocol().withVersion(version));
                } else {
                    acceptedResponseProtocols = TreePVector.empty();
                    for (MessageProtocol accept : request.acceptedResponseProtocols()) {
                        acceptedResponseProtocols = acceptedResponseProtocols.plus(accept.withVersion(version));
                    }
                }

                return request.withUri(newUri)
                        .withProtocol(request.protocol().withVersion(version))
                        .withAcceptedResponseProtocols(acceptedResponseProtocols);

            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        } else {
            return request;
        }
    }

    @Override
    public RequestHeader transformClientRequest(RequestHeader request) {
        if (request.protocol().version().isPresent()) {
            // Just read the version from the request protocol
            String version = request.protocol().version().get();
            URI uri = request.uri();
            String path = String.format(pathVersionFormat, version, uri.getPath());
            try {
                URI newUri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), path,
                        uri.getQuery(), uri.getFragment());

                return request.withUri(newUri);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        } else {
            return request;
        }
    }
}
