/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.server;

import com.lightbend.lagom.internal.javadsl.*;
import com.lightbend.lagom.internal.javadsl.server.ResolvedServices;
import com.lightbend.lagom.internal.javadsl.server.ResolvedServicesProvider;
import com.lightbend.lagom.internal.javadsl.server.ServiceInfoProvider;
import com.lightbend.lagom.internal.javadsl.server.JavadslServicesRouter;
import com.lightbend.lagom.internal.server.status.MetricsServiceImpl;

import com.lightbend.lagom.javadsl.server.status.MetricsService;
import com.google.inject.Binder;
import com.lightbend.lagom.javadsl.api.ServiceInfo;
import com.lightbend.lagom.javadsl.client.ServiceClientGuiceSupport;

public interface ServiceGuiceSupport extends ServiceClientGuiceSupport {

    default void bindServices(ServiceBinding<?>... serviceBindings) {
        Binder binder = BinderAccessor.binder(this);
        for (ServiceBinding binding: serviceBindings) {
            // First, bind the client implementation.  A service should be able to be a client to itself.
            bindClient(binding.serviceInterface());

            // Now, bind the server implementation to itself as an eager singleton.
            if (binding instanceof ClassServiceBinding) {
                binder.bind(((ClassServiceBinding<?>) binding).serviceImplementation).asEagerSingleton();
            } else {
                Object service = ((InstanceServiceBinding<?>) binding).service;
                binder.bind((Class<Object>) service.getClass()).toInstance(service);
            }
        }

        // Bind the service info for the first one passed in
        binder.bind(ServiceInfo.class).toProvider(new ServiceInfoProvider(serviceBindings[0].serviceInterface()));

        // Bind the metrics
        ServiceBinding<MetricsService> metricsServiceBinding = serviceBinding(MetricsService.class, MetricsServiceImpl.class);
        binder.bind(((ClassServiceBinding<?>) metricsServiceBinding).serviceImplementation).asEagerSingleton();
        ServiceBinding<?>[] allServiceBindings = new ServiceBinding<?>[serviceBindings.length + 1];
        System.arraycopy(serviceBindings, 0, allServiceBindings, 0, serviceBindings.length);
        allServiceBindings[allServiceBindings.length-1] = metricsServiceBinding;

        // Bind the resolved services
        binder.bind(ResolvedServices.class).toProvider(new ResolvedServicesProvider(allServiceBindings));

        // And bind the router
        binder.bind(JavadslServicesRouter.class);
    }

    default <T> ServiceBinding<T> serviceBinding(Class<T> serviceInterface, Class<? extends T> serviceImplementation) {
        return new ClassServiceBinding<>(serviceInterface, serviceImplementation);
    }

    default <T> ServiceBinding<T> serviceBinding(Class<T> serviceInterface, T service) {
        return new InstanceServiceBinding<>(serviceInterface, service);
    }

    abstract class ServiceBinding<T> {
        private ServiceBinding() {}
        public abstract Class<T> serviceInterface();
    }

    final class ClassServiceBinding<T> extends ServiceBinding<T> {
        private final Class<T> serviceInterface;
        private final Class<? extends T> serviceImplementation;

        private ClassServiceBinding(Class<T> serviceInterface, Class<? extends T> serviceImplementation) {
            this.serviceInterface = serviceInterface;
            this.serviceImplementation = serviceImplementation;
        }

        @Override
        public Class<T> serviceInterface() {
            return serviceInterface;
        }

        public Class<? extends T> serviceImplementation() {
            return serviceImplementation;
        }
    }

    final class InstanceServiceBinding<T> extends ServiceBinding<T> {
        private final Class<T> serviceInterface;
        private final T service;

        public InstanceServiceBinding(Class<T> serviceInterface, T service) {
            this.serviceInterface = serviceInterface;
            this.service = service;
        }

        @Override
        public Class<T> serviceInterface() {
            return serviceInterface;
        }

        public T service() {
            return service;
        }
    }

}
