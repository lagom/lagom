# Using HTTPS

### Using HTTPS in development mode

When running Lagom in [[Development Mode|DevEnvironment]] it is possible to enable HTTPS via settings on your build files. In Maven use:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <configuration>
        <serviceEnableSsl>true</serviceEnableSsl>
    </configuration>
</plugin>
```

or in sbt:

@[service-enable-ssl](code/build-service.sbt)

This will enable the HTTPS transport next to HTTP. See [[Configuring Services In Development|ConfiguringServicesInDevelopment]] for more details on port assignment in Dev mode.

You can also tune the port the server is bound to (similarly to the HTTP port):

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <configuration>
        <serviceHttpsPort>30443</serviceHttpsPort>
    </configuration>
</plugin>
```

or in sbt:

@[service-https-port](code/build-service.sbt)

Once enabled, your Lagom services will be directly usable with HTTPS. At the moment, the Lagom Service Gateway is only bound to HTTP.

Lagom's development mode instruments the process and injects a self-signed certificate. At same time, the lagom services running in dev mode are automatically tuned to trust that certificate so that you can use service-to-service HTTPS calls. 

The Lagom service client uses HTTP or HTTPS depending on the Service Locator you used. You can create your own HTTPS client using Play-WS or the Akka-HTTP Client API. Then, you should do a lookup on the service locator stating you need an HTTPS port and connect normally using Play-WS or Akka-HTTP Client. If you use Akka gRPC for inter-service communication, you may need to use HTTPS.

### Using HTTPS in tests

To open an SSL port on the `TestServer` used in your tests, you have to enable SSL support using `withSsl`:

```java
Setup.defaultSetup.withSsl() // TODO: review this syntax and move to an actual snippet before merging
```

Enabling SSL will automatically open a new random port and provide an `javax.net.ssl.SSLContext` on the TestServer. Lagom doesn't provide any client factory that allows sending requests to the HTTPS port at the moment. You should create an HTTP client using Play-WS, Akka-HTTP or Akka-gRPC. Then, use the `httpsPort` and the `sslContext` provided by the `testServer` instance to send the request. Note that the `SSLContext` provided is built by Lagom's testkit to trust the `testServer` certificates. Finally, because the server certificate is issued for `CN=localhost` you will have to make sure that's the `authority` on the requests you generate, otherwise the server may decline and fail the request. At the moment it is not possible to setup the test server with different SSL Certificates.  

TODO: example usage // complete this when the PR introducing the improvements is merged and we can write and compile docs-test code.
