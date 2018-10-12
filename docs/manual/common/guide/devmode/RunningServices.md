# Running services

As outlined in the [[development environment introduction|DevEnvironment]], all Lagom services defined in a build can be run with a single task: `runAll`. When executing this task, an embedded [[Service Locator|ServiceLocator]] is started, an embedded [[Cassandra server|CassandraServer]] is also started, and then all your services are started, in parallel. Furthermore, all started services will be running in hot-reload mode. Hot-reload means that the services are automatically reloaded on every change you make, so that you don't have to manually restart them.

Most times, the `runAll` task will serve you well. However, there will be occasions when you may want to manually start only a few services, and this is when the `run` task will come in handy. The `run` task is available to each of your Lagom service implementation projects.

In Maven, you can execute the `run` task on a particular service by using the maven project list flag, like so:

```
$ mvn -pl <your-project-name> lagom:run
```

In sbt, you can specify the project to run on the sbt console by simply prefixing the service project's name, i.e.:

```
$ sbt
> <your-project-name>/run
```

Remember that `run` only starts the specified service. Neither the Service Locator nor the Cassandra server start implicitly. Hence, consider manually starting both the [[Service Locator|ServiceLocator#Start-and-stop]] and the [[Cassandra server|CassandraServer#Start-and-stop]] prior to manually running other services.
