# How are addresses bound by services?

By default, Lagom services bind to `localhost`. This address can be changed with the following.

@[service-address](code/configuring-devmode-services.sbt)

# How are ports assigned to services?

When inspecting the list of running services, you may wonder how ports are being assigned. One thing you should notice is that ports are assigned consistently, meaning that each service will get the same port assigned. This is truly useful, as it allows to write scripts that exercise some service's functionality, and even share the created scripts with the rest of your team. Indeed, the same port is deterministically selected even on different machines!

The algorithm used for assigning a port to each service works as follows:

1) The project's name is hashed.
2) The hash absolute value is projected on the port range (the default port range is `[49152,65535]`).
3) The selected port is assigned to the project, if there is no other project claiming the same port. If two or more projects are projected onto the same port, the conflicting projects are alphabetically sorted, and the project that comes first will get the expected port assigned to it. While, the remaining projects will get assigned the closest (strictly increasing) available neighbour port.

In general, you don't need to worry about these details, as in most cases the port range is wide enough to make collisions unlikely. However, there are times when you may still prefer to assign a specific port to a service (for instance, if the automatically assigned port is already is use in your system). You can do so by manually providing a port number for the project's service port setting.

@[service-port](code/configuring-devmode-services.sbt)

Above, in the algorithm's description, it was mentioned that by default ports are assigned within the range `[49152,65535]`. This is also known as the ephemeral port range, a range of port numbers set aside by IANA for dynamic port selection use. If the default range doesn't suit you, you can change it by adding the following in your build.

@[port-range](code/configuring-devmode-services.sbt)

After this change, your service projects will get assigned a port in the range `[40000,45000]`. But mind that the smaller is the range, the higher are the chances that two or more project will claim the same port. This is not an issue in itself (as long as there are enough ports for all projects), but it is possible that adding a new service project in your build may provoke a change to the port assigned to an existing service project, if both projects happen to claim the same port. If you don't want this to happen, make sure the provided port range is wide enough. Alternatively, manually assign ports to service projects as it makes sense.

# Using HTTPS in development mode

When running Lagom in [[Development Mode|DevEnvironment]] it is possible to enable HTTPS via settings on your build files. In sbt use:

@[service-enable-ssl](code/build-service.sbt)

This will enable the HTTPS transport next to HTTP. 

You can also tune the port the server is bound to (similarly to the HTTP port):

@[service-https-port](code/build-service.sbt)

Once enabled, your Lagom services will also be accessible over HTTPS. At the moment, the Lagom Service Gateway is only bound to HTTP.

Lagom's development mode instruments the process and injects a self-signed certificate. At same time, the Lagom services running in dev mode are automatically tuned to trust that certificate so that you can use service-to-service HTTPS calls.

The Lagom service client uses HTTP in development mode. You can create your own HTTPS client using Play-WS or the Akka-HTTP Client API. Then, you should do a lookup on the service locator stating you need an HTTPS port and connect normally using Play-WS or Akka-HTTP Client. If you use Akka gRPC for inter-service communication, you may need to use HTTPS.
