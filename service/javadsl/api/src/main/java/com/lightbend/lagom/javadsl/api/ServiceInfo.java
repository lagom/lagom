/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.api;

import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Public information for this service. A 3rd party registration could use this to register the
 * service on a service registry.
 *
 * <p>This info requires a name and a locatable service. A locatable service is a named group of
 * {@link ServiceAcl}s.
 *
 * <pre>{@code
 * ServiceAcl helloAcl = ServiceAcl.methodAndPath(Method.GET, "?/hello/.*");
 * ServiceAcl loginAcl = ServiceAcl.methodAndPath(Method.POST, "/login"));
 * ServiceAcl logoutAcl = ServiceAcl.methodAndPath(Method.POST, "/logout/.*");
 *
 * ServiceInfo helloService = ServiceInfo.of("GreetingService", helloAcl, loginAcl, logoutAcl);
 * }</pre>
 */
public final class ServiceInfo {

  private final String serviceName;

  private final PMap<String, PSequence<ServiceAcl>> locatableServices;

  private ServiceInfo(String serviceName, PSequence<ServiceAcl> acls) {
    this.serviceName = serviceName;
    this.locatableServices =
        HashTreePMap.<String, PSequence<ServiceAcl>>empty().plus(serviceName, acls);
  }

  /**
   * Factory method to create ServiceInfo instances that contain a single locatable service.
   *
   * @param serviceName the service name
   * @param acls for the single locatableService of this Service.
   * @return a service info instance with the given {@link ServiceAcl}.
   */
  public static ServiceInfo of(String serviceName, ServiceAcl... acls) {
    return new ServiceInfo(serviceName, TreePVector.from(Arrays.asList(acls)));
  }

  /**
   * Factory method to create ServiceInfo instances that contain a single locatable service.
   *
   * @param serviceName the service name
   * @param acls for the single locatableService of this Service.
   * @return a service info instance with the given {@link ServiceAcl}.
   */
  public static ServiceInfo of(String serviceName, PSequence<ServiceAcl> acls) {
    return new ServiceInfo(serviceName, acls);
  }

  public String serviceName() {
    return serviceName;
  }

  /** @return a complete flattened list of ACLs listing all ACLs in this service info. */
  public PSequence<ServiceAcl> getAcls() {
    return TreePVector.from(
        locatableServices.values().stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toList()));
  }
}
