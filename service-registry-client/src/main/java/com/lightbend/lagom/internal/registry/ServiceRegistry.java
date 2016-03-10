/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.registry;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.pathCall;
import static com.lightbend.lagom.javadsl.api.Service.restCall;

import java.util.Optional;

import org.pcollections.PSequence;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;

public interface ServiceRegistry extends Service {

	String SERVICE_NAME = "lagom-service-registry";

	ServiceCall<String, ServiceRegistryService, NotUsed> register();
	ServiceCall<String, NotUsed, NotUsed> unregister();
	ServiceCall<String, NotUsed, Optional<String>> lookup(); 
	ServiceCall<NotUsed, NotUsed, PSequence<RegisteredService>> registeredServices();
	
	@Override
	default Descriptor descriptor() {
		// @formatter:off
		return named(SERVICE_NAME)
	      .with(restCall(Method.PUT, "/services/:id", register()))
		  .with(restCall(Method.DELETE, "/services/:id", unregister()))
		  .with(restCall(Method.GET, "/services/:id", lookup()))
		  .with(pathCall("/services", registeredServices())
        ).withLocatableService(false);
		// @formatter:on
	}
}
