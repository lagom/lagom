/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.client;

import com.lightbend.lagom.internal.javadsl.BinderAccessor;
import com.lightbend.lagom.internal.javadsl.client.ServiceClientProvider;
import com.lightbend.lagom.javadsl.api.ServiceInfo;

import javax.inject.Singleton;

public interface ServiceClientGuiceSupport {

    default <T> void bindClient(Class<T> clientInterface) {
        BinderAccessor.binder(this).bind(clientInterface)
                .toProvider(new ServiceClientProvider<T>(clientInterface))
                .in(Singleton.class);
    }

    /**
     * Provides the ServiceInfo to use Lagom service clients.
     */
    default void bindServiceInfo(ServiceInfo serviceInfo) {
        BinderAccessor.binder(this).bind(ServiceInfo.class)
                .toInstance(serviceInfo);
    }

}
