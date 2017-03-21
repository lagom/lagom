/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.client;

import com.lightbend.lagom.internal.javadsl.BinderAccessor;
import com.lightbend.lagom.internal.javadsl.client.ServiceClientProvider;
import com.lightbend.lagom.javadsl.api.ServiceInfo;

import javax.inject.Singleton;

/**
 * Applications that already use Guice as their DI framework may implement this interface to bind clients for Lagom
 * services.
 */
public interface ServiceClientGuiceSupport {

    /**
     * Binds a client for <code>clientInterface</code> which Guice's Injector makes available
     * so client classes may declare the dependency on their <code>@Inject</code> annotated constructor.
     * <p>
     * Applications that want to consume Lagom services must provide a {@link ServiceInfo}. Using
     * {@link ServiceClientGuiceSupport#bindClient(Class)} requires providing a {@link ServiceInfo} via
     * {@link ServiceClientGuiceSupport#bindServiceInfo(ServiceInfo)}.
     */
    default <T> void bindClient(Class<T> clientInterface) {
        BinderAccessor.binder(this).bind(clientInterface)
                .toProvider(new ServiceClientProvider<T>(clientInterface))
                .in(Singleton.class);
    }

    /**
     * Registers a {@link ServiceInfo} for this application. This step is required to interact with Lagom services.
     * <p>
     * This method must be invoked exactly once.
     *
     * @param serviceInfo the metadata identifying this Lagom Service.
     */
    default void bindServiceInfo(ServiceInfo serviceInfo) {
        BinderAccessor.binder(this).bind(ServiceInfo.class)
                .toInstance(serviceInfo);
    }

}
