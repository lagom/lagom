/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.client.integration;

import akka.actor.ActorSystem;
import akka.actor.CoordinatedShutdown;
import akka.japi.function.Effect;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import play.api.internal.libs.concurrent.CoordinatedShutdownSupport;
import com.lightbend.lagom.internal.client.*;
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory;
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactoryProvider;
import com.lightbend.lagom.internal.javadsl.client.CircuitBreakersPanelImpl;
import com.lightbend.lagom.internal.javadsl.client.JavadslServiceClientImplementor;
import com.lightbend.lagom.internal.javadsl.client.JavadslWebSocketClient;
import com.lightbend.lagom.internal.javadsl.client.ServiceClientLoader;
import com.lightbend.lagom.internal.javadsl.registry.JavaServiceRegistryClient;
import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistry;
import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistryServiceLocator;
import com.lightbend.lagom.internal.registry.ServiceRegistryClient;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.ServiceInfo;
import com.lightbend.lagom.javadsl.api.ServiceLocator;
import com.lightbend.lagom.javadsl.broker.kafka.KafkaTopicFactory;
import com.lightbend.lagom.javadsl.client.CircuitBreakersPanel;
import com.lightbend.lagom.javadsl.client.CircuitBreakingServiceLocator;
import com.lightbend.lagom.javadsl.jackson.JacksonExceptionSerializer;
import com.lightbend.lagom.javadsl.jackson.JacksonSerializerFactory;
import com.typesafe.config.Config;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.Configuration;
import play.api.Environment;
import play.api.Mode;
import play.api.inject.ApplicationLifecycle;
import play.api.libs.ws.WSClient;
import play.api.libs.ws.WSClientConfig;
import play.api.libs.ws.WSConfigParser;
import play.api.libs.ws.ahc.AhcWSClient;
import play.api.libs.ws.ahc.AhcWSClientConfig;
import play.api.libs.ws.ahc.AhcWSClientConfigParser;
import scala.Function0;
import scala.Some;
import scala.collection.immutable.Map$;
import scala.concurrent.Future;

import java.io.Closeable;
import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Factory for creating Lagom service clients.
 *
 * <p>Generally, there should be only one instance of this per system. Additionally, since it holds
 * thread and connection pools, it must be shutdown, by calling the {@link #close()} method, when no
 * longer needed.
 */
public class LagomClientFactory implements Closeable {

  private final Logger log = LoggerFactory.getLogger(LagomClientFactory.class);

  private final EventLoopGroup eventLoop;
  private final WSClient wsClient;
  private final WebSocketClient webSocketClient;
  private final ActorSystem actorSystem;
  private final CircuitBreakersPanel circuitBreakersPanel;
  private final Function<ServiceLocator, ServiceClientLoader> serviceClientLoaderCreator;
  private final boolean managedActorSystem;

  private LagomClientFactory(
      EventLoopGroup eventLoop,
      WSClient wsClient,
      WebSocketClient webSocketClient,
      ActorSystem actorSystem,
      CircuitBreakersPanel circuitBreakersPanel,
      Function<ServiceLocator, ServiceClientLoader> serviceClientLoaderCreator,
      boolean managedActorSystem) {
    this.eventLoop = eventLoop;
    this.wsClient = wsClient;
    this.webSocketClient = webSocketClient;
    this.actorSystem = actorSystem;
    this.circuitBreakersPanel = circuitBreakersPanel;
    this.serviceClientLoaderCreator = serviceClientLoaderCreator;
    this.managedActorSystem = managedActorSystem;
  }

  /**
   * Create a Lagom service client for the given client interface using the given service locator.
   *
   * @param clientInterface The client interface for the service.
   * @param serviceLocator The service locator.
   * @return An implementation of the client interface.
   */
  public <T> T createClient(Class<T> clientInterface, ServiceLocator serviceLocator) {
    return serviceClientLoaderCreator.apply(serviceLocator).loadServiceClient(clientInterface);
  }

  /**
   * Create a Lagom service client that uses the given URI.
   *
   * @param clientInterface The client interface for the service.
   * @param serviceUri The URI that the service lives at.
   * @return An implementation of the client interface.
   */
  public <T> T createClient(Class<T> clientInterface, URI serviceUri) {
    return serviceClientLoaderCreator
        .apply(new StaticServiceLocator(circuitBreakersPanel, serviceUri))
        .loadServiceClient(clientInterface);
  }

  /**
   * Create a Lagom service client that uses the given URIs.
   *
   * <p>The URIs are used in a round robin fashion. No validation is done to verify that a
   * particular URI is still valid.
   *
   * @param clientInterface The client interface for the service.
   * @param serviceUris The URIs that the service lives at. A copy of this collection will be used,
   *     so changes to the collection after invoking this have no effect.
   * @return An implementation of the client interface.
   */
  public <T> T createClient(Class<T> clientInterface, Collection<URI> serviceUris) {
    return serviceClientLoaderCreator
        .apply(new RoundRobinServiceLocator(circuitBreakersPanel, TreePVector.from(serviceUris)))
        .loadServiceClient(clientInterface);
  }

  /**
   * Create a Lagom service client that uses the Lagom dev mode service locator to locate the
   * service.
   *
   * <p>This uses the default Lagom service locator port, that is, localhost:9008.
   *
   * @param clientInterface The client interface for the service.
   * @return An implementation of the client interface.
   */
  public <T> T createDevClient(Class<T> clientInterface) {
    return createDevClient(clientInterface, URI.create("http://localhost:9008"));
  }

  /**
   * Create a Lagom service client that uses the Lagom dev mode service locator to locate the
   * service.
   *
   * @param clientInterface The client interface for the service.
   * @param serviceLocatorUri The URI of the Lagom dev mode service locator - usually
   *     http://localhost:9008.
   * @return An implementation of the client interface.
   */
  public <T> T createDevClient(Class<T> clientInterface, URI serviceLocatorUri) {
    ServiceRegistry serviceRegistry =
        serviceClientLoaderCreator
            .apply(new StaticServiceLocator(circuitBreakersPanel, serviceLocatorUri))
            .loadServiceClient(ServiceRegistry.class);
    ServiceRegistryClient client =
        new JavaServiceRegistryClient(serviceRegistry, actorSystem.dispatcher());

    ServiceLocator serviceLocator =
        new ServiceRegistryServiceLocator(circuitBreakersPanel, client, actorSystem.dispatcher());

    return serviceClientLoaderCreator.apply(serviceLocator).loadServiceClient(clientInterface);
  }

  /**
   * Close all resources associated with this client factory.
   *
   * <p>This must be invoked when the client factory is no longer needed, otherwise threads and
   * connections will leak.
   */
  public void close() {
    closeGracefully(wsClient::close);
    closeGracefully(webSocketClient::shutdown);
    closeGracefully(this::coordinatedShutdown);
    closeGracefully(() -> eventLoop.shutdownGracefully(0, 10, TimeUnit.SECONDS));
  }

  static class ClientStoppedReason implements CoordinatedShutdown.Reason {}

  private void coordinatedShutdown() throws InterruptedException, TimeoutException {
    if (managedActorSystem)
      CoordinatedShutdownSupport.syncShutdown(actorSystem, new ClientStoppedReason());
  }

  private static LagomClientFactory create(
      String serviceName,
      Environment environment,
      Config configuration,
      ActorSystem actorSystem,
      Materializer materializer,
      boolean managedActorSystem) {

    // Netty event loop
    EventLoopGroup eventLoop = new NioEventLoopGroup();

    // WS
    WSClientConfig wsClientConfig =
        new WSConfigParser(configuration, environment.classLoader()).parse();

    AhcWSClientConfig ahcWSClientConfig =
        new AhcWSClientConfigParser(wsClientConfig, configuration, environment.classLoader())
            .parse();

    WSClient wsClient = AhcWSClient.apply(ahcWSClientConfig, scala.Option.empty(), materializer);

    // WebSocketClient
    WebSocketClientConfig webSocketClientConfig =
        WebSocketClientConfig$.MODULE$.apply(configuration);
    // Use dummy lifecycle, we manage the lifecycle manually
    ApplicationLifecycle applicationLifecycle =
        new ApplicationLifecycle() {
          @Override
          public void addStopHook(Function0<Future<?>> hook) {}

          @Override
          public void addStopHook(Callable<? extends CompletionStage<?>> hook) {}

          @Override
          public play.inject.ApplicationLifecycle asJava() {
            return new play.inject.DelegateApplicationLifecycle(this);
          }

          @Override
          public Future<?> stop() {
            return null;
          }
        };

    JavadslWebSocketClient webSocketClient =
        new JavadslWebSocketClient(
            environment,
            webSocketClientConfig,
            eventLoop,
            applicationLifecycle,
            actorSystem.dispatcher());

    // TODO: review this. Building a kafka client shouldn't require the whole ServiceInfo, just the
    // name.
    ServiceInfo serviceInfo = ServiceInfo.of(serviceName);

    // ServiceClientLoader
    CircuitBreakersPanel circuitBreakersPanel =
        new CircuitBreakersPanelImpl(
            actorSystem,
            new CircuitBreakerConfig(configuration),
            new CircuitBreakerMetricsProviderImpl(actorSystem));

    JacksonSerializerFactory serializerFactory = new JacksonSerializerFactory(actorSystem);
    JacksonExceptionSerializer exceptionSerializer =
        new JacksonExceptionSerializer(new play.Environment(environment));

    Function<ServiceLocator, ServiceClientLoader> serviceClientLoaderCreator =
        serviceLocator -> {
          // Kafka client
          TopicFactory kafkaTopicFactory =
              new KafkaTopicFactory(
                  serviceInfo,
                  actorSystem,
                  materializer,
                  actorSystem.dispatcher(),
                  serviceLocator,
                  configuration);

          TopicFactoryProvider topicFactoryProvider = () -> Some.apply(kafkaTopicFactory);

          JavadslServiceClientImplementor implementor =
              new JavadslServiceClientImplementor(
                  wsClient,
                  webSocketClient,
                  serviceInfo,
                  serviceLocator,
                  environment,
                  topicFactoryProvider,
                  actorSystem.dispatcher(),
                  materializer);

          return new ServiceClientLoader(
              serializerFactory, exceptionSerializer, environment, implementor);
        };

    return new LagomClientFactory(
        eventLoop,
        wsClient,
        webSocketClient,
        actorSystem,
        circuitBreakersPanel,
        serviceClientLoaderCreator,
        managedActorSystem);
  }

  private static Environment buildtEnvironment(ClassLoader classLoader) {
    return Environment.apply(new File("."), classLoader, Mode.Prod$.MODULE$);
  }

  private static Config buildConfig(ClassLoader classLoader) {
    return Configuration.load(classLoader, System.getProperties(), Map$.MODULE$.empty(), true)
        .underlying();
  }
  /**
   * Creates a Lagom client factory.
   *
   * <p>Generally, there should be only one instance of this per system. Additionally, since it
   * holds thread and connection pools, it must be shutdown, by calling the {@link #close()} method,
   * when no longer needed.
   *
   * <p>This method should be used whenever your application has already a running ActorSystem. In
   * that case it's preferable to reuse it inside LagomClientFactory instead to let the factory
   * create and manage its own (see {@link #create(String, ClassLoader)}).
   *
   * <p>Calling {@link #close()} on a {@link LagomClientFactory} created using this method will NOT
   * terminated the passed {@link ActorSystem} and Akka Streams {@link Materializer}.
   *
   * @param serviceName The name of this service that is going to be accessing the services created
   *     by this factory.
   * @param classLoader A classloader, it will be used to create the service proxy and needs to have
   *     the API for the client in it.
   * @param actorSystem An existing {@link ActorSystem}
   * @param materializer An existing Akka Streams {@link Materializer}
   * @return The client factory.
   */
  public static LagomClientFactory create(
      String serviceName,
      ClassLoader classLoader,
      ActorSystem actorSystem,
      Materializer materializer) {
    return create(
        serviceName,
        buildtEnvironment(classLoader),
        buildConfig(classLoader),
        actorSystem,
        materializer,
        /*managedActorSystem*/ false);
  }

  /**
   * Creates a Lagom client factory.
   *
   * <p>Generally, there should be only one instance of this per system. Additionally, since it
   * holds thread and connection pools, it must be shutdown, by calling the {@link #close()} method,
   * when no longer needed.
   *
   * <p>This method should be used whenever your application does NOT have a running {@link
   * ActorSystem} and you don't want to manage one yourself.
   *
   * <p>Internally, this method will create a new {@link ActorSystem} that will be attached to the
   * lifecycle of this factory. In other words, the internal {@link ActorSystem} will terminate upon
   * calling {@link #close()}.
   *
   * <p>In case your application already have a running {@link ActorSystem}, we recommend to use
   * {@link #create(String, ClassLoader, ActorSystem, Materializer)}) instead.
   *
   * @param serviceName The name of this service that is going to be accessing the services created
   *     by this factory.
   * @param classLoader A classloader, it will be used to create the service proxy and needs to have
   *     the API for the client in it.
   * @return The client factory.
   */
  public static LagomClientFactory create(String serviceName, ClassLoader classLoader) {
    // Environment and config
    Environment environment = buildtEnvironment(classLoader);
    Config configuration = buildConfig(classLoader);

    // Akka
    ActorSystem actorSystem = ActorSystem.create("lagom-client", configuration, classLoader);
    Materializer materializer = ActorMaterializer.create(actorSystem);

    return create(
        serviceName,
        environment,
        configuration,
        actorSystem,
        materializer,
        /*managedActorSystem*/ true);
  }

  private void closeGracefully(Effect close) {
    try {
      close.apply();
    } catch (Exception e) {
      log.warn("Error shutting down LagomClientFactory component", e);
    }
  }

  private static class StaticServiceLocator extends CircuitBreakingServiceLocator {
    private final URI uri;

    StaticServiceLocator(CircuitBreakersPanel circuitBreakersPanel, URI uri) {
      super(circuitBreakersPanel);
      this.uri = uri;
    }

    @Override
    public CompletionStage<Optional<URI>> locate(String name, Descriptor.Call<?, ?> serviceCall) {
      return CompletableFuture.completedFuture(Optional.of(uri));
    }
  }

  private static class RoundRobinServiceLocator extends CircuitBreakingServiceLocator {
    private final PVector<URI> uris;
    private final AtomicInteger counter = new AtomicInteger(0);

    RoundRobinServiceLocator(CircuitBreakersPanel circuitBreakersPanel, PVector<URI> uris) {
      super(circuitBreakersPanel);
      this.uris = uris;
    }

    private URI nextUri() {
      int index = Math.abs(counter.getAndIncrement() % uris.size());
      return uris.get(index);
    }

    @Override
    public CompletionStage<Optional<URI>> locate(String name, Descriptor.Call<?, ?> serviceCall) {
      return CompletableFuture.completedFuture(Optional.of(nextUri()));
    }
  }
}
