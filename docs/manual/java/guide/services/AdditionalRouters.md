# Additional Routers

Since Lagom 1.5.0, is it possible to extend a Lagom Service with additional Play Routers. 

This is particular useful when itegrating Lagom with existing Play Routers, for instance a [Play gRPC Router](https://developer.lightbend.com/docs/play-grpc/0.5.0/lagom/serving-grpc.html) , or any other Play router that you have at your disposal.

In Java, you add an additional router when binding your Lagom Service in your Guice module. You should pass the additonal routers to the `bindService` method together with the Service interface and its implementation. You do this by means of a helper method called `additonalRouters`. 

There are two variants for `additionalRouters`, one that receives a `Class<play.api.routing.Router>` and one that receives a `play.api.routing.Router`.

The first one should be used when your Router has some dependencies and needs to get them injected  by Lagom's runtime DI infrastructure (Guice). In this case, you pass the class and Guice will initialize it with the right dependencies.


```java
public class HelloWorldModule extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        bindService(
                HelloService.class, HelloServiceImpl.class,
                additionalRouter(SomeOtherPlayRouter.class)
        );
    }
}
```

The second variant should be used be used when the Router does not have any other dependencies and therefore can be immediately passed as an instance.

```java
public class HelloWorldModule extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        bindService(
                HelloService.class, HelloServiceImpl.class,
                additionalRouter(new SomeOtherPlayRouter())
        );
    }
}
```



## File Upload Example

The following example shows how you can add a file upload endpoint to an existing Lagom Service. 

The example is based on [JavaRoutingDsl](https://www.playframework.com/documentation/2.7.x/JavaRoutingDsl) that allows you to build a Play Router programmatically. It adds an extra path (`/api/files`) that receives POST calls for multipart-form data. 

```java
class FileUploadRouter implements SimpleRouter {

    private final Router delegate;

    @Inject
    public FileUploadRouter(RoutingDsl routingDsl) {
        this.delegate = routingDsl
                .POST("/api/files")
                .routingTo( __ -> {
                    // for the sake of simplicity, this implementation
                    // only returns a short message for each incoming request.
                    return ok("File(s) uploaded");
                })
                .build().asScala();
    }

    @Override
    public PartialFunction<RequestHeader, Handler> routes() {
        return delegate.routes();
    }
}
```

In your Guice module, you append the additional router when binding your Lagom service.

```java
public class HelloWorldModule extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        bindService(
                HelloWorldService.class, HelloWorldServiceImpl.class,
                additionalRouter(FileUploadRouter.class)
        );
    }
}

```

The path `/api/files` will now be exposed on your Lagom service:

```bash
curl -X POST -F "data=@somefile.txt" -v  http://localhost:65499/api/files
```

> Note that in that example we are not using the Service Gateway to access the application. We are calling it directly using the service port (ie: 65499).

## Service Gateway Considerations

An additional router is not part of your application `ServiceDescriptor` and therefore can't be automatically published as endpoints to the Service Gateway in development mode. 

If you want to access your additional routers thourgh the gateway, you will need to explicitly add the ACL (Access Control List) for it in your `ServiceDescriptor` definition.

```java
public interface HelloService extends Service {

  ServiceCall<NotUsed, String> hello(String id);

  @Override
  default Descriptor descriptor() {
    return named("hello")
             .withCalls(
               pathCall("/api/hello/:id", this::hello)
             )
             .withAutoAcl(true)
             .withServiceAcls(path("/api/files"));
  }
}
```

Once the path is published on the Service Gateway, you can call: 

```bash
curl -X POST -F "data=@somefile.txt" -v  http://localhost:9000/api/files
```

> Note usage of port 9000 (Lagom's Dev Mode ServiceGateway)

## Lagom Client Considerations

Additional routers are not part of the Service API and therefore are not accessbile from generated Lagom clients. Lagom clients only have access to methods defined on the Service interface. 

Additional routers are only part of the exposed HTTP endpoints.

 