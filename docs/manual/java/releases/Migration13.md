# Lagom 1.3 Migration Guide

This guide explains hot to migrate from Lagom 1.2 to Lagom 1.3

## Service Info 

It is now compulsory to provide a `ServiceInfo` to run in the Development Environment. This is required because Lagom's `$ sbt runAll` acts as [[3rd party registrator|ServiceDiscovery]] and needs information of all the microservices it is running.

This change affects Play apps run in Lagom's Dev Env using `$ sbt runAll`. It is now necessary to provide a `ServiceInfo`, so if you were not implementing a `Module` you will need to add one. The suggested location is `<play_app_folder>/app/Module.java` so that Guice will find it automatically.

