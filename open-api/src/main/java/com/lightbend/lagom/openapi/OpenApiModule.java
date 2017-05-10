/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.openapi;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.lightbend.lagom.internal.javadsl.server.JavadslServiceRouter;
import com.lightbend.lagom.internal.javadsl.server.JavadslServiceRouter.JavadslServiceRoute;
import com.lightbend.lagom.internal.javadsl.server.JavadslServicesRouter;
import com.lightbend.lagom.javadsl.api.transport.Method;

import io.swagger.models.Info;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.RefModel;
import io.swagger.models.Response;
import io.swagger.models.Swagger;
import io.swagger.models.properties.RefProperty;
import scala.collection.JavaConverters;

public class OpenApiModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(Swagger.class).toProvider(SwaggerProvider.class).asEagerSingleton();
  }

  private static class SwaggerProvider implements Provider<Swagger> {

    private final JavadslServicesRouter servicesRouter;

    @Inject
    public SwaggerProvider(JavadslServicesRouter servicesRouter) {
      this.servicesRouter = servicesRouter;
    }

    @Override
    public Swagger get() {
      Swagger swagger = new Swagger();

      // TODO(benmccann): info title and version are required properties
      Info info = new Info();
      swagger.setInfo(info);

      for (JavadslServiceRouter serviceRouter : JavaConverters.asJavaCollectionConverter(
          servicesRouter.serviceRouters()).asJavaCollection()) {

        for (JavadslServiceRoute serviceRoute : JavaConverters.asJavaCollectionConverter(
            serviceRouter.serviceRoutes()).asJavaCollection()) {

          Operation operation = new Operation();

          // TODO(benmccann): response description is required
          // should the user express non-200 return codes via annotations?
          Response response = new Response();
          operation.setResponses(ImmutableMap.of("200", response));

          Class<?> returnType = serviceRoute.holder().method().getReturnType();
          if (!returnType.equals(Void.TYPE)) {
            RefModel responseModel = new RefModel();
            swagger.addDefinition(returnType.getSimpleName(), responseModel);
            response.setSchema(new RefProperty("#/definitions/" + returnType.getSimpleName()));
          }

          // TODO(benmccann): add parameters
          Path path = new Path();
          if (Method.GET.equals(serviceRoute.method())) {
            path.setGet(operation);
          } else if (Method.POST.equals(serviceRoute.method())) {
            path.setPost(operation);
          } else if (Method.PUT.equals(serviceRoute.method())) {
            path.setPut(operation);
          } else if (Method.DELETE.equals(serviceRoute.method())) {
            path.setDelete(operation);
          } else if (Method.PATCH.equals(serviceRoute.method())) {
            path.setPatch(operation);
          } else if (Method.HEAD.equals(serviceRoute.method())) {
            path.setHead(operation);
          } else if (Method.OPTIONS.equals(serviceRoute.method())) {
            path.setOptions(operation);
          }
          swagger.path(toSwaggerTemplatedPath(serviceRoute.path().pathSpec()), path);
        }
      }

      return swagger;
    }

    /**
     * Converts from Lagom's /foo/:var to Swagger's /foo/{var}
     */
    private String toSwaggerTemplatedPath(String path) {
      return Joiner.on("/").join(
          Arrays.stream(path.split("/"))
              .map(segment -> segment.startsWith(":") ? "{" + segment.substring(1) + "}" : segment)
              .collect(Collectors.toList()));
    }
    
  }

}
