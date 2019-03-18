# Additional Routers

Since Lagom 1.5.0, it is possible to extend a Lagom Service with additional Play Routers.

This is particularly useful when integrating Lagom with existing Play Routers, for instance a [Play gRPC Router](https://developer.lightbend.com/docs/play-grpc/0.6.0/lagom/serving-grpc.html?language=java), or any other Play router that you have at your disposal.

You add an additional router when binding your Lagom Service in your Guice module. You should pass the additional routers to the `bindService` method together with the Service interface and its implementation. You do this by means of a helper method called `additionalRouters`.

There are two variants for `additionalRouters`, one that receives a `Class<play.api.routing.Router>` and one that receives an instance of `play.api.routing.Router`.

The first one should be used when your Router has some dependencies and needs to get them injected  by Lagom's runtime DI infrastructure (Guice). In this case, you pass the class and Guice will initialize it with the right dependencies.

@[lagom-module-some-play-router-DI](code/docs/services/AdditionalRouters.java)

The second variant should be used when the Router does not have any other dependencies and therefore can be immediately passed as an instance.

@[lagom-module-some-play-router-instance](code/docs/services/AdditionalRouters.java)


## File Upload Example

The following example shows how you can add a file upload endpoint to an existing Lagom Service.

The example is based on [JavaRoutingDsl](https://www.playframework.com/documentation/2.7.x/JavaRoutingDsl) that allows you to build a Play Router programmatically. It adds an extra path (`/api/files`) that receives POST calls for multipart-form data.

@[file-upload-router](code/docs/services/AdditionalRouters.java)

In your Guice module, you append the additional router when binding your Lagom service.

@[lagom-module-file-upload](code/docs/services/AdditionalRouters.java)

The path `/api/files` will now be available on your Lagom service:

```bash
curl -X POST -F "data=@somefile.txt" -v  http://localhost:65499/api/files
```

> Note that in that example we are not using the Service Gateway to access the application. We are calling it directly using the service port, in this case, `65499`.

## Service Gateway Considerations

An additional router is not part of your application `ServiceDescriptor` and therefore can't be automatically published as endpoints to the [[Service Gateway|ServiceLocator#Service-Gateway]] in development mode.

If you want to access your additional routers through the gateway, you will need to explicitly add the ACL (Access Control List) for it in your `ServiceDescriptor` definition.


@[hello-service](code/docs/services/AdditionalRouters.java)

Once the path is published on the Service Gateway, you can call:

```bash
curl -X POST -F "data=@somefile.txt" -v  http://localhost:9000/api/files
```

> Note usage of port 9000 (Lagom's Dev Mode ServiceGateway)

## Lagom Client Considerations

Additional routers are not part of the Service API and therefore are not accessible from generated Lagom clients. Lagom clients only have access to methods defined on the Service interface.

Additional routers are only part of the exposed HTTP endpoints. To access then, you will need to use an HTTP client, eg: [Play-WS](https://www.playframework.com/documentation/2.7.x/JavaWS)
