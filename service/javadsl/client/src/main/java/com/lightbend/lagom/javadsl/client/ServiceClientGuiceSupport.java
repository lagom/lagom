/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.client;

import com.lightbend.lagom.internal.javadsl.client.ServiceClientProvider;
import com.lightbend.lagom.internal.javadsl.BinderAccessor;

import javax.inject.Singleton;

public interface ServiceClientGuiceSupport {

    default <T> void bindClient(Class<T> clientInterface) {
        BinderAccessor.binder(this).bind(clientInterface)
                .toProvider(new ServiceClientProvider<T>(clientInterface))
                .in(Singleton.class);
    }

}
