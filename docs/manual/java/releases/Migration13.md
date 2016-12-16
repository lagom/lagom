# Lagom 1.3 Migration Guide

This guide explains hot to migrate from Lagom 1.2 to Lagom 1.3

## Service Info 

It is now compulsory to provide a `ServiceInfo` to run in the Development Environment. This is required because Lagom's `$ sbt runAll` acts as [[3rd party registrator##ServiceDiscovery]] and needs information of all the microservices it is running.

This changes slightly the `bindServices()` API. While you can still use the `bindServices(ServiceBinding... bindings)` method it is discouraged (and deprecated) and `bindServices(String serviceName, ServiceBinding... bindings)` is provided. The new argument `serviceName` is the name of the microservice containing all the services you specify in the `bindings` argument.

This change also affects Play apps run in Lagom's Dev Env using `$ sbt runAll`. It is now necessary to provide a `ServiceInfo` so if you were not implementing a `Module` you will need to add one. The suggested location is `<play_app_folder>/app/Module.java` so that Guice will find it automatically.

If you develop an application that's not run inside Lagom's Dev Env but uses Lagom Services Clients via `ServiceClientGuiceSupport` and `bindClient` you will need to provide a `ServiceInfo` too.