/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.client.integration;

import akka.actor.ActorSystem;
import akka.japi.Effect;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.lightbend.lagom.internal.client.CircuitBreakerConfig;
import com.lightbend.lagom.internal.client.CircuitBreakerMetricsProviderImpl;
import com.lightbend.lagom.internal.client.WebSocketClient;
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory;
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactoryProvider;
import com.lightbend.lagom.internal.javadsl.client.JavadslServiceClientImplementor;
import com.lightbend.lagom.internal.javadsl.client.JavadslWebSocketClient;
import com.lightbend.lagom.internal.javadsl.client.ServiceClientLoader;
import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistry;
import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistryServiceLocator;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.ServiceInfo;
import com.lightbend.lagom.javadsl.api.ServiceLocator;
import com.lightbend.lagom.javadsl.broker.kafka.KafkaTopicFactory;
import com.lightbend.lagom.javadsl.client.CircuitBreakersPanel;
import com.lightbend.lagom.internal.javadsl.client.CircuitBreakersPanelImpl;
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

import play.api.Environment;
import play.api.Mode;
import play.api.Configuration;
import play.api.inject.ApplicationLifecycle;
import play.api.libs.ws.WSClient;
import play.api.libs.ws.WSClientConfig;
import play.api.libs.ws.WSConfigParser;
import play.api.libs.ws.ahc.AhcWSClient;
import play.api.libs.ws.ahc.AhcWSClientConfig;
import play.api.libs.ws.ahc.AhcWSClientConfigParser;
import scala.Function0;
import scala.Some;
import scala.concurrent.Future;

import java.io.Closeable;
import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Factory for creating Lagom service clients.
 *
 * This is designed for use from non Lagom systems.
 *
 * Generally, there should be only one instance of this per system.  Additionally, since it holds thread and connection
 * pools, it must be shutdown, by calling the {@link #close()} method, when no longer needed.
 */
public class LagomClientFactory implements Closeable {

    private final Logger log = LoggerFactory.getLogger(LagomClientFactory.class);

    private final EventLoopGroup eventLoop;
    private final WSClient wsClient;
    private final WebSocketClient webSocketClient;
    private final ActorSystem actorSystem;
    private final CircuitBreakersPanel circuitBreakersPanel;
    private final Function<ServiceLocator, ServiceClientLoader> serviceClientLoaderCreator;

    private LagomClientFactory(EventLoopGroup eventLoop, WSClient wsClient, WebSocketClient webSocketClient, ActorSystem actorSystem,
                               CircuitBreakersPanel circuitBreakersPanel, Function<ServiceLocator, ServiceClientLoader> serviceClientLoaderCreator) {
        this.eventLoop = eventLoop;
        this.wsClient = wsClient;
        this.webSocketClient = webSocketClient;
        this.actorSystem = actorSystem;
        this.circuitBreakersPanel = circuitBreakersPanel;
        this.serviceClientLoaderCreator = serviceClientLoaderCreator;
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
        return serviceClientLoaderCreator.apply(new StaticServiceLocator(circuitBreakersPanel, serviceUri))
                .loadServiceClient(clientInterface);
    }

    /**
     * Create a Lagom service client that uses the given URIs.
     *
     * The URIs are used in a round robin fashion. No validation is done to verify that a particular URI is still
     * valid.
     *
     * @param clientInterface The client interface for the service.
     * @param serviceUris The URIs that the service lives at.  A copy of this collection will be used, so changes to the
     *            collection after invoking this have no effect.
     * @return An implementation of the client interface.
     */
    public <T> T createClient(Class<T> clientInterface, Collection<URI> serviceUris) {
        return serviceClientLoaderCreator.apply(new RoundRobinServiceLocator(circuitBreakersPanel,
                TreePVector.from(serviceUris))).loadServiceClient(clientInterface);
    }

    /**
     * Create a Lagom service client that uses the Lagom dev mode service locator to locate the service.
     *
     * This uses the default Lagom service locator port, that is, localhost:9008.
     *
     * @param clientInterface The client interface for the service.
     * @return An implementation of the client interface.
     */
    public <T> T createDevClient(Class<T> clientInterface) {
        return createDevClient(clientInterface, URI.create("http://localhost:9008"));
    }

    /**
     * Create a Lagom service client that uses the Lagom dev mode service locator to locate the service.
     *
     * @param clientInterface The client interface for the service.
     * @param serviceLocatorUri The URI of the Lagom dev mode service locator - usually http://localhost:9008.
     * @return An implementation of the client interface.
     */
    public <T> T createDevClient(Class<T> clientInterface, URI serviceLocatorUri) {
        ServiceRegistry serviceRegistry = serviceClientLoaderCreator.apply(new StaticServiceLocator(circuitBreakersPanel,
                serviceLocatorUri)).loadServiceClient(ServiceRegistry.class);

        ServiceLocator serviceLocator = new ServiceRegistryServiceLocator(circuitBreakersPanel, serviceRegistry,
                new ServiceRegistryServiceLocator.ServiceLocatorConfig(serviceLocatorUri), actorSystem.dispatcher());

        return serviceClientLoaderCreator.apply(serviceLocator).loadServiceClient(clientInterface);
    }

    /**
     * Close all resources associated with this client factory.
     *
     * This must be invoked when the client factory is no longer needed, otherwise threads and connections will leak.
     */
    public void close() {
        closeGracefully(wsClient::close);
        closeGracefully(webSocketClient::shutdown);
        closeGracefully(actorSystem::terminate);
        closeGracefully(() -> eventLoop.shutdownGracefully(0, 10, TimeUnit.SECONDS));
    }

    /**
     * Create a client factory that uses the given service locator.
     *
     * @param serviceName The name of this service that is going to be accessing the services created by this factory.
     * @param classLoader The classloader.
     * @return The client factory.
     */
    public static LagomClientFactory create(String serviceName, ClassLoader classLoader) {
        // Environment and config
        Environment environment = Environment.apply(new File("."), classLoader, Mode.Prod$.MODULE$);
        Config configuration = Configuration.load(environment).underlying();

        // Akka
        ActorSystem actorSystem = ActorSystem.create("lagom-client", configuration.getConfig("akka"),
                classLoader);
        Materializer materializer = ActorMaterializer.create(actorSystem);

        // Netty event loop
        EventLoopGroup eventLoop = new NioEventLoopGroup();

        // WS
        WSClientConfig wsClientConfig = new WSConfigParser(configuration, environment.classLoader()).parse();
        AhcWSClientConfig ahcWSClientConfig = new AhcWSClientConfigParser(wsClientConfig, configuration, environment.classLoader()).parse();
        WSClient wsClient = AhcWSClient.apply(ahcWSClientConfig, scala.Option.empty(), materializer);

        // WebSocketClient
        // Use dummy lifecycle, we manage the lifecycle manually
        JavadslWebSocketClient webSocketClient = new JavadslWebSocketClient(environment, eventLoop, new ApplicationLifecycle() {
            @Override
            public void addStopHook(Function0<Future<?>> hook) {
            }
            @Override
            public void addStopHook(Callable<? extends CompletionStage<?>> hook) {
            }
            @Override
            public play.inject.ApplicationLifecycle asJava() {
                return new play.inject.DelegateApplicationLifecycle(this);
            }
            @Override
            public Future<?> stop() {
                return null;
            }
        }, actorSystem.dispatcher());

        // TODO: review this. Building a kafka client shouldn't require the whole ServiceInfo, just the name.
        ServiceInfo serviceInfo = ServiceInfo.of(serviceName);

        // ServiceClientLoader
        CircuitBreakersPanel circuitBreakersPanel =
                new CircuitBreakersPanelImpl(
                        actorSystem,
                        new CircuitBreakerConfig(configuration),
                        new CircuitBreakerMetricsProviderImpl(actorSystem)
                );


        JacksonSerializerFactory serializerFactory = new JacksonSerializerFactory(actorSystem);
        JacksonExceptionSerializer exceptionSerializer = new JacksonExceptionSerializer(new play.Environment(environment));

        Function<ServiceLocator, ServiceClientLoader> serviceClientLoaderCreator = serviceLocator -> {
            // Kafka client
            TopicFactory kafkaTopicFactory = new KafkaTopicFactory(serviceInfo, actorSystem, materializer,
                    actorSystem.dispatcher(), serviceLocator);
            TopicFactoryProvider topicFactoryProvider = () -> Some.apply(kafkaTopicFactory);

            JavadslServiceClientImplementor implementor = new JavadslServiceClientImplementor(wsClient, webSocketClient, serviceInfo,
                    serviceLocator, environment, topicFactoryProvider, actorSystem.dispatcher(), materializer);
            return new ServiceClientLoader(serializerFactory, exceptionSerializer, environment, implementor);

        };
        return new LagomClientFactory(eventLoop, wsClient, webSocketClient, actorSystem, circuitBreakersPanel,
                serviceClientLoaderCreator);
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
