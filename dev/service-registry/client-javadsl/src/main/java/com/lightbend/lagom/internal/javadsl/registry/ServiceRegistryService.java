/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.registry;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A service to be registered by the service registry
 */
public class ServiceRegistryService {
    private final URI uri;
    private final List<ServiceAcl> acls;

    public static ServiceRegistryService of(URI uri, List<com.lightbend.lagom.javadsl.api.ServiceAcl> acls) {
        List<ServiceAcl> internalAcls =
            acls.stream().map(api -> {
                    Optional<Method> method = api.method().map(m -> new Method(m.name()));
                    return new ServiceAcl(method, api.pathRegex());
                }
            ).collect(Collectors.toList());
        return new ServiceRegistryService(uri, internalAcls);
    }

    public ServiceRegistryService(URI uri, List<ServiceAcl> acls) {
        this.uri = uri;
        this.acls = acls;
    }

    public URI uri() {
        return uri;
    }

    public List<ServiceAcl> acls() {
        return acls;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceRegistryService)) return false;

        ServiceRegistryService that = (ServiceRegistryService) o;

        if (!uri.equals(that.uri)) return false;
        return acls.equals(that.acls);

    }

    @Override
    public int hashCode() {
        int result = uri.hashCode();
        result = 31 * result + acls.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ServiceRegistryService{" +
            "uri='" + uri + '\'' +
            ", acls=" + acls +
            '}';
    }
}
