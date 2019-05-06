/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.registry;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/** A service to be registered by the service registry */
public class ServiceRegistryService {
  private final List<URI> uris;
  private final List<ServiceAcl> acls;

  public static ServiceRegistryService of(
      URI uri, List<com.lightbend.lagom.javadsl.api.ServiceAcl> acls) {
    return of(Collections.singletonList(uri), acls);
  }

  public static ServiceRegistryService of(
      List<URI> uris, List<com.lightbend.lagom.javadsl.api.ServiceAcl> acls) {
    List<ServiceAcl> internalAcls =
        acls.stream()
            .map(
                acl -> {
                  Optional<Method> method = acl.method().map(m -> new Method(m.name()));
                  return new ServiceAcl(method, acl.pathRegex());
                })
            .collect(Collectors.toList());
    return new ServiceRegistryService(uris, internalAcls);
  }

  public ServiceRegistryService(URI uri) {
    this(uri, Collections.emptyList());
  }

  public ServiceRegistryService(URI uri, List<ServiceAcl> acls) {
    this(Arrays.asList(uri), acls);
  }

  @JsonCreator
  public ServiceRegistryService(List<URI> uris, List<ServiceAcl> acls) {
    this.uris = uris;
    this.acls = acls;
  }

  public List<URI> uris() {
    return uris;
  }

  public List<ServiceAcl> acls() {
    return acls;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ServiceRegistryService)) return false;

    ServiceRegistryService that = (ServiceRegistryService) o;

    if (!uris.equals(that.uris)) return false;
    return acls.equals(that.acls);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uris, acls);
  }

  @Override
  public String toString() {
    return "ServiceRegistryService{" + "uri='" + uris + '\'' + ", acls=" + acls + '}';
  }
}
