/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api;

import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * Public information for this service. A 3rd party registration could use this to register the service on a
 * service registry.
 * </p>
 * <p>
 * This info requires a name and a group of locatable services. A locatable service is
 * a named group of {@link ServiceAcl}s.
 * </p>
 * <pre>
 * {@code
 *         PSequence<ServiceAcl> helloAcls = TreePVector.from(Arrays.asList(
 *              ServiceAcl.methodAndPath(Method.GET, "?/hello/.*"),
 *              ServiceAcl.methodAndPath(Method.POST, "/login"))
 *         );
 *         PSequence<ServiceAcl> goodbyeAcls = TreePVector.singleton(
 *              ServiceAcl.methodAndPath(Method.POST, "/logout/.*")
 *         );
 *
 *         PMap<String, PSequence<ServiceAcl>> locatableServices =
 *              HashTreePMap.<String, PSequence<ServiceAcl>>empty()
 *                  .plus("hello-service", helloAcls)
 *                  .plus("goodbye-service", goodbyeAcls);
 *         new ServiceInfo("GreetingService", locatableServices);
 * }
 * </pre>
 */
public final class ServiceInfo {

    private final String serviceName;

    private final PMap<String, PSequence<ServiceAcl>> locatableServices;

    /**
     * @deprecated use {@link ServiceInfo#ServiceInfo(String, PMap)} instead.
     */
    @Deprecated
    public ServiceInfo(String serviceName) {
        this(serviceName, HashTreePMap.empty());
    }


    /**
     * @param serviceName       identifies this service. This is the default id when this service acts as a client.
     * @param locatableServices a group of locatable services. This information should be publicized on a Service
     *                          Registry for either client-side or server-side service discovery.
     * @deprecated use {@link ServiceInfo#of(String, ServiceAcl...)} instead.
     */
    @Deprecated
    public ServiceInfo(String serviceName, PMap<String, PSequence<ServiceAcl>> locatableServices) {
        this.serviceName = serviceName;
        this.locatableServices = locatableServices;
    }

    private ServiceInfo(String serviceName, PSequence<ServiceAcl> acls) {
        this.serviceName = serviceName;
        this.locatableServices = HashTreePMap.<String, PSequence<ServiceAcl>>empty().plus(serviceName, acls);
    }

    /**
     * Factory method to create ServiceInfo instances that contain a single locatable service.
     *
     * @param serviceName
     * @param acls        for the single locatableService of this Service.
     * @return
     */
    public static ServiceInfo of(String serviceName, ServiceAcl... acls) {
        return new ServiceInfo(serviceName, TreePVector.from(Arrays.asList(acls)));
    }

    public String serviceName() {
        return serviceName;
    }

    /**
     * @deprecated this method will be removed when dropping support for multiple locatable service descriptors per service.
     */
    @Deprecated
    public PMap<String, PSequence<ServiceAcl>> getLocatableServices() {
        return locatableServices;
    }

    /**
     * @return a complete flattened list of ACLs listing all ACLs in this service info.
     */
    public PSequence<ServiceAcl> getAcls() {
        return TreePVector.from(locatableServices.values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
    }

}
