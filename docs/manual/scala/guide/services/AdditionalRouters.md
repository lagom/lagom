# Additional Routers

Since Lagom 1.5.0, is it possible to extend a Lagom Service with additional Play Routers. 

This is particular useful when itegrating Lagom with existing Play Routers, for instance a [Play gRPC Router](https://developer.lightbend.com/docs/play-grpc/0.5.0/lagom/serving-grpc.html) , or any other Play router that you have at your disposal.

In Scala, you add an additional router when wiring your Lagom Server. After wiring the Lagom Server, you append the additional Play routers to it. 

```scala
override lazy val lagomServer =
  serverFor[HelloService](wire[HelloServiceImpl])
    .additionalRouter(someOtherPlayRouter)
```




## File Upload Example

The following example shows how you can add a file upload endpoint to an existing Lagom Service. 

The example is based on [ScalaSirdRouter](https://www.playframework.com/documentation/2.7.x/ScalaSirdRouter) that allows you to build a Play Router programmatically. It adds an extra path (`/api/files`) that receives POST calls for multipart-form data. 

```scala
import play.api.mvc.{DefaultActionBuilder, Results}
import play.api.routing.Router
import play.api.routing.sird._

class FileUploadRouter(action: DefaultActionBuilder) {
  val router = Router.from {
    case POST(p"/api/files") => action { _ =>
      // for the sake of simplicity, this implementation 
      // only returns a short message for each incoming request. 
      Results.Ok("File(s) uploaded")
    }
  }
}
```

In your application loader, you can wire the router and append it to your Lagom server.

```scala
override lazy val lagomServer =
  serverFor[HelloService](wire[HelloServiceImpl])
    .additionalRouter(wire[FileUploadRouter].router)
```

The path `/api/files` will now be exposed on your Lagom service:

```bash
curl -X POST -F "data=@somefile.txt" -v  http://localhost:65499/api/files
```

> Note that in that example we are not using the Service Gateway to access the application. We are calling it directly using the service port (ie: 65499).

## Service Gateway Considerations

An additional router is not part of your application `ServiceDescriptor` and therefore can't be automatically published as endpoints to the Service Gateway in development mode. 

If you want to access your additional routers thourgh the gateway, you will need to explicitly add the ACL (Access Control List) for it in your `ServiceDescriptor` definition.

```scala
trait HelloService extends Service {

  def hello(id: String): ServiceCall[NotUsed, String]
  override final def descriptor = {
    import Service._
    named("hello")
      .withCalls(
        pathCall("/api/hello/:id", hello _).withAutoAcl(true)
      )
      .withAcls(
        // extra ACL to expose additional router endpoint on ServiceGateway  
        ServiceAcl(pathRegex = Some("/api/files"))
      )
  }
}
```

Once the path is published on the Service Gateway, you can call: 

```bash
curl -X POST -F "data=@somefile.txt" -v  http://localhost:9000/api/files
```

> Note usage of port 9000 (Lagom's Dev Mode ServiceGateway)

## Lagom Client Considerations

Additional routers are not part of the Service API and therefore are not accessbile from generated Lagom clients. Lagom clients only have access to methods defined on the Service trait. 

Additional routers are only part of the exposed HTTP endpoints.

 



