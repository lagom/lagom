# Additional Routers

Since Lagom 1.5.0, it is possible to extend a Lagom Service with additional Play Routers.

This is particularly useful when integrating Lagom with existing Play Routers, for instance a [Play gRPC Router](https://developer.lightbend.com/docs/play-grpc/0.6.0/lagom/serving-grpc.html?language=scala) , or any other Play router that you have at your disposal.

You add an additional router when wiring your Lagom Server. After wiring the Lagom Server, you append the additional Play routers to it.

@[lagom-application-some-play-router](code/AdditionalRouters.scala)

## File Upload Example

The following example shows how you can add a file upload endpoint to an existing Lagom Service.

The example is based on [ScalaSirdRouter](https://www.playframework.com/documentation/2.7.x/ScalaSirdRouter) that allows you to build a Play Router programmatically. It adds an extra path (`/api/files`) that receives POST calls for multipart-form data.

@[file-upload-router](code/AdditionalRouters.scala)

In your application loader, you can wire the router and append it to your Lagom server.

@[lagom-application-file-upload](code/AdditionalRouters.scala)

The path `/api/files` will now be available on your Lagom service:

```bash
curl -X POST -F "data=@somefile.txt" -v  http://localhost:65499/api/files
```

> Note that in that example we are not using the Service Gateway to access the application. We are calling it directly using the service port, in this case, 65499.

## Service Gateway Considerations

An additional router is not part of your application `ServiceDescriptor` and therefore can't be automatically published as endpoints to the [[Service Gateway|ServiceLocator#Service-Gateway]] in development mode.

If you want to access your additional routers through the gateway, you will need to explicitly add the ACL (Access Control List) for it in your `ServiceDescriptor` definition.

@[hello-service](code/AdditionalRouters.scala)

Once the path is published on the Service Gateway, you can call:

```bash
curl -X POST -F "data=@somefile.txt" -v  http://localhost:9000/api/files
```

> Note usage of port 9000 (Lagom's Dev Mode ServiceGateway)

## Lagom Client Considerations

Additional routers are not part of the Service API and therefore are not accessible from generated Lagom clients. Lagom clients only have access to methods defined on the Service trait.

Additional routers are only part of the exposed HTTP endpoints. To access then, you will need to use an HTTP client, eg: [Play-WS](https://www.playframework.com/documentation/2.7.x/ScalaWS)
