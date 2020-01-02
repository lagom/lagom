/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.server;

import akka.annotation.ApiMayChange;
import akka.annotation.InternalApi;
import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import com.lightbend.lagom.internal.javadsl.BinderAccessor;
import com.lightbend.lagom.internal.javadsl.server.*;
import com.lightbend.lagom.internal.server.status.MetricsServiceImpl;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceInfo;
import com.lightbend.lagom.javadsl.client.ServiceClientGuiceSupport;
import com.lightbend.lagom.javadsl.server.status.MetricsService;
import play.api.routing.Router;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Lagom service implementations must create one implementation of this interface and use it to bind
 * Service implementations.
 *
 * <p>Implementors of this interface must invoke {@link ServiceGuiceSupport#bindService}, {@link
 * ServiceGuiceSupport#bindServices(ServiceBinding[])} or {@link
 * ServiceGuiceSupport#bindServiceInfo(ServiceInfo)} exactly-once depending on the type of Lagom
 * service being implemented (1 service, many services, consume-only). These methods setup the
 * service and may transparently add cross-cutting services like {@link MetricsService} (allows
 * monitoring circuit-breakers from the outside).
 */
public interface ServiceGuiceSupport extends ServiceClientGuiceSupport {

  /**
   * Helper method to create an {@link AdditionalRouter} instance that can be used to declare
   * additional Play {@link Router}s on a Lagom Service.
   *
   * <p>This method should be used when the {@link Router} has some dependencies and needs to get
   * them injected by Lagom's runtime DI infrastructure (Guice).
   *
   * <p>Typically, this will be a {@link Router} generated from a Play {@code routes} or a akka-grpc
   * generated Play {@link Router}.
   *
   * <p>Once you declare a {@link Router}, you may need to define its prefix to indicate on which
   * path it should be available.
   *
   * <pre>
   * bindService(
   *     MyService.class, MyServiceImpl.class,
   *     additionalRouter(HelloWorldRouter.class).withPrefix("/hello"),
   * );
   * </pre>
   *
   * You don't need to configure a prefix if the {@link Router} has it pre-configured. A akka-grpc
   * generated Play {@link Router}, for instance, has its prefix already defined by the gRPC
   * descriptor and doesn't need to have its prefix reconfigured.
   *
   * <p>Note that this method won't create a binding and is intended to be used in conjunction with
   * {@link ServiceGuiceSupport#bindService(Class, Service, AdditionalRouter, AdditionalRouter...)}
   * or {@link ServiceGuiceSupport#bindService(Class, Class, AdditionalRouter,
   * AdditionalRouter...)}. Calling this method outside this context will have no effect.
   *
   * @param router an additional Play Router class
   * @return an AdditionalRouter instance
   */
  @ApiMayChange
  default <R extends Router> AdditionalRouter additionalRouter(Class<R> router) {
    return new ClassBased<R>(router);
  }

  /**
   * Helper method to create an {@link AdditionalRouter} instance that can be used to declare
   * additional Play {@link Router}s on a Lagom Service.
   *
   * <p>This method should be used when the {@link Router} does not have any other dependencies and
   * therefore can be immediately passed as an instance.
   *
   * <p>Once you declare a {@link Router}, you may need to define its prefix to indicate on which
   * path it should be available.
   *
   * <pre>
   * bindService(
   *     MyService.class, MyServiceImpl.class,
   *     additionalRouter(new HelloWorldRouter()).withPrefix("/hello"),
   * );
   * </pre>
   *
   * You don't need to configure a prefix if the {@link Router} has it pre-configured.
   *
   * <p>Note that this method won't create a binding and is intended to be used in conjunction with
   * {@link ServiceGuiceSupport#bindService(Class, Service, AdditionalRouter, AdditionalRouter...)}
   * or {@link ServiceGuiceSupport#bindService(Class, Class, AdditionalRouter,
   * AdditionalRouter...)}. Calling this method outside this context will have no effect.
   *
   * @param router an additional Play Router instance
   * @return an AdditionalRouter instance
   */
  @ApiMayChange
  default AdditionalRouter additionalRouter(Router router) {
    return new InstanceBased(router);
  }

  @InternalApi
  class LagomServiceBuilder {

    private ServiceBinding<?> binding;
    private ServiceGuiceSupport guiceSupport;

    private List<AdditionalRouter> additionalRouters = new ArrayList<>();

    private LagomServiceBuilder(ServiceBinding<?> binding, ServiceGuiceSupport guiceSupport) {
      this.binding = binding;
      this.guiceSupport = guiceSupport;
    }

    private LagomServiceBuilder withAdditionalRouters(
        AdditionalRouter additionalRouter, AdditionalRouter... additionalRouters) {
      this.additionalRouters.add(additionalRouter);
      this.additionalRouters.addAll(Arrays.asList(additionalRouters));
      return this;
    }

    public void bind() {

      Binder binder = BinderAccessor.binder(guiceSupport);

      guiceSupport.bindClient(binding.serviceInterface());

      // Now, bind the server implementation to itself as an eager singleton.
      if (binding instanceof ClassServiceBinding) {
        binder.bind(((ClassServiceBinding<?>) binding).serviceImplementation).asEagerSingleton();
      } else {
        Object service = ((InstanceServiceBinding<?>) binding).service;
        binder.bind((Class<Object>) service.getClass()).toInstance(service);
      }

      // Bind the service info for the first one passed in
      binder
          .bind(ServiceInfo.class)
          .toProvider(new ServiceInfoProvider(binding.serviceInterface(), new Class[0]));

      // Bind the metrics
      ServiceBinding<MetricsService> metricsServiceBinding =
          guiceSupport.serviceBinding(MetricsService.class, MetricsServiceImpl.class);
      binder
          .bind(((ClassServiceBinding<?>) metricsServiceBinding).serviceImplementation)
          .asEagerSingleton();

      // Bind the resolved services
      binder
          .bind(ResolvedServices.class)
          .toProvider(
              new ResolvedServicesProvider(
                  new ServiceBinding<?>[] {binding, metricsServiceBinding}));

      // bind the list of AdditionalRouter provided by the user
      binder.bind(new TypeLiteral<List<AdditionalRouter>>() {}).toInstance(additionalRouters);
      // bind a provider that can get the list of AdditionalRouter and a Injector
      // and provide a List<Router>
      binder.bind(new TypeLiteral<List<Router>>() {}).toProvider(AdditionalRoutersProvider.class);

      // And bind the router
      binder.bind(LagomServiceRouter.class).to(JavadslServicesRouter.class);
    }
  }

  /**
   * Creates a custom {@link ServiceInfo} for this Lagom service. This method overrides {@link
   * ServiceClientGuiceSupport#bindServiceInfo(ServiceInfo)} with custom behavior for consume-only
   * Lagom services.
   *
   * @param serviceInfo the metadata identifying this Lagom Service.
   */
  default void bindServiceInfo(ServiceInfo serviceInfo) {
    // copied from super interface since default methods in JAVA can't be invoked from extending
    // interfaces.
    Binder binder = BinderAccessor.binder(this);
    binder.bind(ServiceInfo.class).toInstance(serviceInfo);

    // Bind the metrics
    ServiceBinding<MetricsService> metricsServiceBinding =
        serviceBinding(MetricsService.class, MetricsServiceImpl.class);
    binder
        .bind(((ClassServiceBinding<?>) metricsServiceBinding).serviceImplementation)
        .asEagerSingleton();
    ServiceBinding<?>[] allServiceBindings = {metricsServiceBinding};

    // Bind the resolved services
    binder
        .bind(ResolvedServices.class)
        .toProvider(new ResolvedServicesProvider(allServiceBindings));

    // use empty list of routers
    // no support for additional routers because the purpose of a `bindServiceInfo` is to
    // provide the means to create a consume-only Lagom service
    binder.bind(new TypeLiteral<List<Router>>() {}).toInstance(Collections.emptyList());

    // And bind the router
    binder.bind(LagomServiceRouter.class).to(JavadslServicesRouter.class);
  }

  /**
   * Binds Service interfaces with their implementations and registers them for publishing.
   *
   * <p>Inspects all bindings and creates routes to serve every call described in the bound
   * services.
   *
   * <p>Builds the {@link ServiceInfo} metadata using only the <code>locatable</code> services.
   *
   * @param serviceBindings an arbitrary list of {@link ServiceBinding}s. Use the convenience method
   *     {@link ServiceGuiceSupport#serviceBinding(Class, Class)} to build the {@link
   *     ServiceBinding}s. Despite being a <code>varargs</code> argument, it is required to provide
   *     at least one {@link ServiceBinding} as argument.
   * @deprecated support for multiple locatable ServiceDescriptors per Lagom service will be
   *     removed. Use {@link ServiceGuiceSupport#bindService} instead
   */
  default void bindServices(ServiceBinding<?>... serviceBindings) {
    Binder binder = BinderAccessor.binder(this);

    for (ServiceBinding binding : serviceBindings) {
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

    ServiceBinding<?> primaryServiceBinding = serviceBindings[0];
    // Bind the service info for the first one passed in
    binder
        .bind(ServiceInfo.class)
        .toProvider(
            new ServiceInfoProvider(
                primaryServiceBinding.serviceInterface(),
                Arrays.stream(serviceBindings)
                    .map(ServiceBinding::serviceInterface)
                    .toArray(Class[]::new)));

    // Bind the metrics
    ServiceBinding<MetricsService> metricsServiceBinding =
        serviceBinding(MetricsService.class, MetricsServiceImpl.class);
    binder
        .bind(((ClassServiceBinding<?>) metricsServiceBinding).serviceImplementation)
        .asEagerSingleton();
    ServiceBinding<?>[] allServiceBindings = new ServiceBinding<?>[serviceBindings.length + 1];
    System.arraycopy(serviceBindings, 0, allServiceBindings, 0, serviceBindings.length);
    allServiceBindings[allServiceBindings.length - 1] = metricsServiceBinding;

    // Bind the resolved services
    binder
        .bind(ResolvedServices.class)
        .toProvider(new ResolvedServicesProvider(allServiceBindings));

    // bind empty list
    // NB: bindServices is deprecated for quite some time and we can't
    // add support for additional routers to it because it uses already varargs
    // we will need to come up with another dsl to make it possible, but since it's already
    // deprecated it
    // makes no sense to try to support additional routers for it
    binder.bind(new TypeLiteral<List<Router>>() {}).toInstance(Collections.emptyList());

    // And bind the router
    binder.bind(LagomServiceRouter.class).to(JavadslServicesRouter.class);
  }

  /**
   * Binds a Service interface with its implementation.
   *
   * <p>Inspects the service descriptor and creates routes to serve every call described.
   *
   * <p>Builds the {@link ServiceInfo} metadata.
   *
   * @param serviceInterface the interface class for a {@link Service}
   * @param serviceImplementation the implementation class for the <code>serviceInterface</code>
   * @param <T> type constraint ensuring <code>serviceImplementation</code> implements <code>
   *     serviceInterface</code>
   */
  default <T extends Service> void bindService(
      Class<T> serviceInterface, Class<? extends T> serviceImplementation) {
    new LagomServiceBuilder(serviceBinding(serviceInterface, serviceImplementation), this).bind();
  }

  /**
   * Binds a Service interface with its implementation.
   *
   * <p>Inspects the service descriptor and creates routes to serve every call described.
   *
   * <p>Allows the configuration of additional Play routers.
   *
   * <p>Builds the {@link ServiceInfo} metadata.
   *
   * @param serviceInterface the interface class for a {@link Service}
   * @param serviceImplementation the implementation class for the <code>serviceInterface</code>
   * @param additionalRouter a first AdditionalRouter
   * @param additionalRouters other AdditionalRouter if any (can be omitted)
   * @param <T> type constraint ensuring <code>serviceImplementation</code> implements <code>
   *     serviceInterface</code>
   */
  default <T extends Service> void bindService(
      Class<T> serviceInterface,
      Class<? extends T> serviceImplementation,
      AdditionalRouter additionalRouter,
      AdditionalRouter... additionalRouters) {
    new LagomServiceBuilder(serviceBinding(serviceInterface, serviceImplementation), this)
        .withAdditionalRouters(additionalRouter, additionalRouters)
        .bind();
  }

  /**
   * Binds a Service interface with an instance that implements it.
   *
   * <p>Inspects the service descriptor and creates routes to serve every call described.
   *
   * <p>Builds the {@link ServiceInfo} metadata.
   *
   * @param serviceInterface the interface class for a {@link Service}
   * @param service an instance of a class implementing <code>serviceInterface</code>
   * @param <T> type constraint ensuring <code>serviceImplementation</code> implements <code>
   *     serviceInterface</code>
   */
  default <T extends Service> void bindService(Class<T> serviceInterface, T service) {
    new LagomServiceBuilder(serviceBinding(serviceInterface, service), this).bind();
  }

  /**
   * Binds a Service interface with an instance that implements it.
   *
   * <p>Inspects the service descriptor and creates routes to serve every call described.
   *
   * <p>Allows the configuration of additional Play routers. {@link AdditionalRouter} can be
   * configured using {@link ServiceGuiceSupport#additionalRouter(Class)} or {@link
   * ServiceGuiceSupport#additionalRouter(Router)}.
   *
   * <p>Builds the {@link ServiceInfo} metadata.
   *
   * @param serviceInterface the interface class for a {@link Service}
   * @param service an instance of a class implementing <code>serviceInterface</code>
   * @param additionalRouter a first AdditionalRouter
   * @param additionalRouters other AdditionalRouter if any (can be omitted)
   * @param <T> type constraint ensuring <code>serviceImplementation</code> implements <code>
   *     serviceInterface</code>
   */
  default <T extends Service> void bindService(
      Class<T> serviceInterface,
      T service,
      AdditionalRouter additionalRouter,
      AdditionalRouter... additionalRouters) {
    new LagomServiceBuilder(serviceBinding(serviceInterface, service), this)
        .withAdditionalRouters(additionalRouter, additionalRouters)
        .bind();
  }

  // TODO: move all code related to ServiceBinding to an internal package when removing
  // bindServices(...)

  /**
   * Convenience method to create {@link ServiceGuiceSupport.ServiceBinding} when using {@link
   * ServiceGuiceSupport#bindServices(ServiceBinding[])}.
   *
   * @return a {@link ServiceGuiceSupport.ServiceBinding} to be used as argument in {@link
   *     ServiceGuiceSupport#bindServices(ServiceBinding[])}.
   */
  default <T> ServiceBinding<T> serviceBinding(
      Class<T> serviceInterface, Class<? extends T> serviceImplementation) {
    return new ClassServiceBinding<>(serviceInterface, serviceImplementation);
  }

  /**
   * Convenience method to create {@link ServiceGuiceSupport.ServiceBinding} when using {@link
   * ServiceGuiceSupport#bindServices(ServiceBinding[])}.
   *
   * @param serviceInterface the interface class for a service
   * @param service an instance of the service
   * @param <T> type constraint ensuring <code>service</code> implements <code>serviceInterface
   *     </code>
   * @return a {@link ServiceGuiceSupport.ServiceBinding} to be used as argument in {@link
   *     ServiceGuiceSupport#bindServices(ServiceBinding[])}.
   */
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

    private ClassServiceBinding(
        Class<T> serviceInterface, Class<? extends T> serviceImplementation) {
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
