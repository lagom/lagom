# Lagom 1.3 Migration Guide

This guide explains hot to migrate from Lagom 1.2 to Lagom 1.3

## Service Info 

It is now compulsory to provide a [[ServiceInfo|ServiceInfo]] programmatically to run in the Development Environment. If you were providing `lagom.play.service-name` and `lagom.play.acls` via `aplication.conf` on your Play application the Developer Mode's `runAll` will still work but this is now deprecated. If your Play app is not implementing a `Module` you should add one and use the new `bindServiceInfo` to setup the service name and `ServiceAcl`s. The suggested location is `<play_app_folder>/app/Module.java` so that Guice will find it automatically.

