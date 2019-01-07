/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.registry.impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.gateway.ServiceGatewayConfig;
import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistry;
import com.lightbend.lagom.javadsl.api.ServiceLocator;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import play.libs.akka.AkkaGuiceSupport;

import java.util.Collections;
import java.util.Map;

public class ServiceRegistryModule extends AbstractModule implements ServiceGuiceSupport, AkkaGuiceSupport {

	private final ServiceGatewayConfig serviceGatewayConfig;
	private final Map<String,String> unmanagedServices;

	public ServiceRegistryModule(ServiceGatewayConfig serviceGatewayConfig, Map<String,String> unmanagedServices) {
		this.serviceGatewayConfig = serviceGatewayConfig;
		this.unmanagedServices = Collections.unmodifiableMap(unmanagedServices);
	}

	public static final String SERVICE_REGISTRY_ACTOR = "serviceRegistryActor";

	@Override
	protected void configure() {
		bindService(ServiceRegistry.class, ServiceRegistryImpl.class);
		bindActor(ServiceRegistryActor.class, SERVICE_REGISTRY_ACTOR);
		bind(ServiceGatewayConfig.class).toInstance(serviceGatewayConfig);
		bind(UnmanagedServices.class).toInstance(UnmanagedServices.apply(unmanagedServices));
		bind(ServiceLocator.class).to(NoServiceLocator.class);
	}
}
