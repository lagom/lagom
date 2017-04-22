# Service Metadata

Service metadata, also referred to as `ServiceInfo`, includes a name and a collection of ACLs. Metadata is computed automatically in most scenarios and you won't need to review it or even provide it.

There are several scenarios supported by Lagom:

1. When you create a Lagom Service and you use the [[bindService|ServiceImplementation]] method to bind a service Lagom will bundle the `name` and the ACLs of the Service Descriptor into a `ServiceInfo`.
2. When you create a Lagom Service but you don't bind any service you should use the [[bindServiceInfo|ServiceClients#Binding-a-service-client]] method and provide the metadata manually.
3. When you consume Lagom Services from an app that already uses Guice, you simply [[bind a Service Client|ServiceClients#Binding-a-service-client]]. In this scenario Lagom is not bundling a ServiceInfo under the covers and you will have to provide one programmatically.
4. The final scenario is that where the client app is not using Guice and connects to Lagom via the [[Lagom Client Factory|IntegratingNonLagom]]. In this scenario, Lagom will also create the metadata on your behalf.


## Service Name and Service ACLs

Services interact with each other. This interaction requires each service to identify itself when acting as a client to another service. When this identity is required, the `ServiceInfo`'s name is used by default. Take for example `HelloService`:

@[hello-service](code/docs/services/HelloService.java)

If Greetings Service packaged `HelloService` and Greetings Service was invoking I18n Service (not in the snippet) those calls would include the identity `hello` since that is the `HelloService` name (see `named("hello")`).

Services may publish ACLs in a Service Gateway to list what endpoints are provided by the service. These ACLs will allow you to develop [[Server-Side Service Discovery|ServiceDiscovery#Server-side-service-discovery]] via a Service Gateway.

@[with-auto-acl](code/docs/services/UsersService.java)

In this example, the developer of `UsersService` set `withAutoAcl` to `true`. That is instructing Lagom to generate Service ACLs from each call's `pathPattern`. In this example, an ACL for `/api/users/login` will be created. When deploying, your tools should honour these specifications and make sure your API Gateway is properly setup.
