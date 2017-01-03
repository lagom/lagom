/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api;

import java.util.*;

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
 *     Map&lt;String, List&lt;ServiceAcl&gt;&gt; locatableServices = new HashMap&lt;&gt;();
 *     List&lt;ServiceAcl&gt; helloAcls = Arrays.asList(
 *        new ServiceAcl(Optional.of(Method.GET), Optional.of("?/hello/.*")),
 *        new ServiceAcl(Optional.of(Method.POST), Optional.of("/login"))
 *        );
 *     List&lt;ServiceAcl&gt; goodbyeAcls = Arrays.asList(
 *       new ServiceAcl(Optional.of(Method.POST), Optional.of("/logout/.*")));
 *
 *     locatableServices.put("hello-service", helloAcls);
 *     locatableServices.put("goodbye-service", goodbyeAcls);
 *
 *     new ServiceInfo("GreetingService",locatableServices);
 * </pre>
 */
public final class ServiceInfo {

    private final String serviceName;

    private final Map<String, List<ServiceAcl>> locatableServices;


    /**
     * @deprecated use {@link ServiceInfo#ServiceInfo(String, Map)} instead
     */
    public ServiceInfo(String serviceName) {
        this(serviceName, new HashMap<>());
    }


    /**
     * @param serviceName       identifies this service. This is the default id when this service act
     * @param locatableServices a group of locatable services. This information should be publicized on a Service
     *                          Registry for either client-side or server-side service discovery.
     * @since 1.3
     */
    public ServiceInfo(String serviceName, Map<String, List<ServiceAcl>> locatableServices) {
        this.serviceName = serviceName;
        this.locatableServices = locatableServices;
    }

    /**
     * Factory method to conveniently create ServiceInfo instances that contain a single locatable service whose name
     * equals the <code>serviceName</code>.
     *
     * @param serviceName
     * @param acls        for the single locatableService of this Service.
     * @return
     */
    public static ServiceInfo of(String serviceName, ServiceAcl... acls) {
        Map<String, List<ServiceAcl>> locatableServices = new HashMap<>();
        locatableServices.put(serviceName, Arrays.asList(acls));
        return new ServiceInfo(serviceName, locatableServices);
    }

    public String serviceName() {
        return serviceName;
    }


    public Map<String, List<ServiceAcl>> getLocatableServices() {
        return locatableServices;
    }

}
