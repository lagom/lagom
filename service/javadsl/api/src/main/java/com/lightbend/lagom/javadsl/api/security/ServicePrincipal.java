/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.security;

import java.security.Principal;

/**
 * A service principal.
 *
 * A service principal may either represent a service identity, or it may be a principal that a service made a request
 * on behalf of.
 */
public interface ServicePrincipal extends Principal {

    /**
     * The service name.
     *
     * This may or may not be the same as {@link #getName()}, depending on whether the principal represents the service
     * itself, or simply a request made by the service on behalf of another principal.
     *
     * @return The server name.
     */
    default String serviceName() {
        return getName();
    }

    /**
     * Whether the service was authenticated.
     *
     * Some service identity strategies simply pull the service name from a header, which can be trivially spoofed.
     * This can be used to distinguish whether the service identity has been established by an insecure means such as
     * that, or a secure means such as client certificates, signed tokens or shared secrets.
     *
     * @return True if the service identity was established by some secure means.
     */
    default boolean authenticated() {
        return false;
    }

    /**
     * Get a service principal for the given named service.
     *
     * @return The service principal.
     */
    static ServicePrincipal forServiceNamed(String serviceName) {
        return () -> serviceName;
    }
}
