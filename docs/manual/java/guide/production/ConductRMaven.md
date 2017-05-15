# Using ConductR with Maven

While ConductR doesn't yet provide built in Maven support, it's still straight forward to manually package your services to deploy to ConductR.

## Packaging your services

The Maven Java archetype generates services that are ready to deploy to ConductR, if you have based your services off that archetype, you should be ready to package your artifacts so that they can be deployed to ConductR.  This guide will run through what's needed if you haven't.

Packaging a bundle in Maven requires the following:

* Creating a bundle configuration file, `bundle.conf`
* Creating a start script
* Creating a Maven assembly plugin descriptor to create the bundle zip
* Binding the Maven assembly plugin and Lagom `renameConductRBundle` goals to your projects lifecycle

### Creating a bundle configuration file

For a full reference for how to create a ConductR bundle configuration file, see [ConductR Bundle Configuration](https://conductr.lightbend.com/docs/2.0.x/BundleConfiguration) documentation.

The bundle configuration file should be called `bundle.conf`, and you can put it in a folder called `src/bundle` in your service.  This location is only conventional, it can live anywhere, but where it lives will impact the Maven assembly descriptor that you create.

Here is an example `bundle.conf`:

```
version = "1"
name = "hello"
compatibilityVersion = "1"
system = "hello"
systemVersion = "1"
nrOfCpus = 0.1
memory = 268435456
diskSpace = 200000000
roles = ["web"]
components = {
  hello = {
    description = "hello"
    file-system-type = "universal"
    start-command = ["hello/bin/hello"]
    endpoints = {
      "hello" = {
        bind-protocol = "http"
        bind-port = 0
        services = ["http://:9000/hello","http://:9000/api/hello?preservePath"]
      },
      "akka-remote" = {
        bind-protocol = "tcp"
        bind-port     = 0
        services      = []
      }
    }
  }
}
```

Some important features to note:

* The `services` property in the `endpoints` list all the paths that get routed from the service gateway to your service.  You will need to ensure that this is kept in sync with the publicly accessible service calls that your service offers.
* The `start-command` is the path (and arguments) to a script that ConductR will invoke to run the service.  We will write that next.

### Creating a start script

Your start script should match the path, relative to the `bundle` directory, specified above in the `bundle.conf` file.  By convention, it should be placed in a `bin` directory, inside a directory with the same name as your service.  For example, it may be a file with the path `src/bundle/hello/bin/hello`.  Here is an example script:

```sh
#!/bin/sh

JVM_OPTS="-Xmx128m -Xms128m"

# This should be changed if you use Play sessions
PLAY_SECRET=none

CONFIG="-Dhttp.address=$HELLO_BIND_IP -Dhttp.port=$HELLO_BIND_PORT -Dplay.crypto.secret=$PLAY_SECRET"

DIR=$(dirname $0)

java -cp "$DIR/../lib/*" $JAVA_OPTS $CONFIG play.core.server.ProdServerStart
```

You can make the start script whatever you want, but there are some important things to note:

* The IP and port that your service should bind to are passed via the environment variables `<endpoint-name>_BIND_IP` and `<endpoint-name>_BIND_PORT`.  The script needs to ensure that these environment variables are used to configure Play's bind address and ports.
* The classpath here is specified to be everything in the `lib` directory under the parent of the `bin` directory.  Later when we package the bundle, this is where we'll put all the jars for the service.

### Creating an assembly descriptor

Having created the files necessary for the bundle, we need to package them together.  The simplest way to do this is using the [Maven assembly plugin](https://maven.apache.org/plugins/maven-assembly-plugin/). For detailed documentation on using the assembly plugin, you should visit the above website, but an example assembly descriptor for use with ConductR would be:

```xml
<assembly xmlns="http://maven.apache.org/plugins/maven-assembly-plugin/assembly/1.1.3"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/plugins/maven-assembly-plugin/assembly/1.1.3 http://maven.apache.org/xsd/assembly-1.1.3.xsd">
    <id>conductr-bundle</id>
    <formats>
        <format>zip</format>
    </formats>
    <baseDirectory>hello-v1</baseDirectory>

    <dependencySets>
        <dependencySet>
            <outputDirectory>hello/lib</outputDirectory>
            <outputFileNameMapping>${artifact.groupId}-${artifact.artifactId}-${artifact.version}${dashClassifier?}.${artifact.extension}</outputFileNameMapping>
        </dependencySet>
    </dependencySets>

    <fileSets>
        <fileSet>
            <outputDirectory></outputDirectory>
            <directory>src/bundle</directory>
            <excludes>
                <exclude>hello/bin/**</exclude>
            </excludes>
        </fileSet>
        <fileSet>
            <outputDirectory>hello/bin</outputDirectory>
            <directory>src/bundle/hello/bin</directory>
            <fileMode>0755</fileMode>
        </fileSet>
    </fileSets>

</assembly>
```

Important things to note here:

* The base directory sets the root directory for bundle - this can be anything, but conventionally it should contain a version number that gets incremented when breaking changes are made to the service.
* The jars are extracted to the `lib` directory inside a directory with the same name as the service name.
* Everything in the `bundle` directory is copied as is.
* Care needs to be taken to ensure the files in the `bin` directory are marked as executable.

### Adding the packaging to your build

Now that we can create the bundle, we need to bind it to our build.  There are two steps to this, first, we need to bind the assembly `single` goal to the `package` phase:

```xml
<plugin>
    <artifactId>maven-assembly-plugin</artifactId>
    <configuration>
        <descriptors>
            <descriptor>src/assembly/conductr-bundle.xml</descriptor>
        </descriptors>
    </configuration>
    <executions>
        <execution>
            <id>conductr-bundle</id>
            <phase>package</phase>
            <goals>
                <goal>single</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

The second step is that the bundle needs to be renamed to include a hash of its contents - this is required by ConductR.  Lagom provides a convenient goal for doing this renaming, but care needs to be taken using it, the rename must happen *after* the bundle is packaged.  Since both the assembly goal and the rename goal will be binding to the `package` phase, according to Maven rules, they will be executed in the order that they appear in the `pom`, with the ordering from parent poms taken precedence.  This means you need to ensure that, if the lagom plugin is configured in a parent pom, that the assembly plugin configuration appears before it in that parent pom.

To bind the Lagom goal to the lifecycle, add an execution to it, like so:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <configuration>
        <lagomService>true</lagomService>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>renameConductRBundle</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Now, when you run `mvn package`, the service will produce a ConductR bundle that you can load into ConductR.

## Loading and running your services during development

First you need to start the local ConductR cluster using the `sandbox run` command:

```console
$ sandbox run <CONDUCTR_VERSION>
```

With the ConductR sandbox running you can load the bundle that you previously generated.  For example:

```console
$ conduct load hello-impl/target/hello-v1-e053f964e359d51dbe6f01f1a84b60e8f664699320d5c1d91d4d0a0e182f5be1.zip
```

Finally, to run the bundle on ConductR use:

```console
$ conduct run hello
Bundle run request sent.
Bundle e053f964e359d51dbe6f01f1a84b60e8 waiting to reach expected scale 1
Bundle e053f964e359d51dbe6f01f1a84b60e8 has scale 0, expected 1
Bundle e053f964e359d51dbe6f01f1a84b60e8 expected scale 1 is met
Stop bundle with: conduct stop --ip 192.168.99.100 9849508
Print ConductR info with: conduct info --ip 192.168.99.100
[success] Total time: 4 s, completed 05/03/2016 2:43:07 PM
```

Now, the Lagom service should run in your local ConductR cluster. The IP address of your cluster is the Docker host IP address. To pick up the IP address check out the previous console output of the `conduct run` command. The default port of a Lagom service on ConductR is `9000`, e.g. considering the ConductR IP address is `192.168.99.100` then the running service is available at `http://192.168.99.100:9000/my/service/path`.

You can also check the state of your cluster with:

```console
$ conduct info
```

The `conduct` command allows you to manage the full lifecycle of a bundle. You can also use `conduct stop hello` and `conduct unload hello` to stop and unload your Lagom services. In addition you can use `conduct logs hello` to view the consolidated logging of bundles throughout the cluster. This is particularly useful during development.

To stop the ConductR sandbox use:

```console
$ sandbox stop
```

## Loading and running your services outside of development

The sandbox is useful to validate that the packaging of your service is correct. However, at some point you want to load and run your bundle on a real ConductR cluster. While it is beyond the scope of this document to describe how to set up such a cluster (please refer to the [ConductR installation guide](https://conductr.lightbend.com/docs/2.0.x/Install) for that), you generally interact with a real cluster through [the ConductR CLI](https://github.com/typesafehub/conductr-cli#command-line-interface-cli-for-typesafe-conductr). You have already downloaded the CLI as part of the sandbox. The CLI commands are identical to their sbt console counterparts. Type `conduct --help` for more information on what commands are available.

## Running Cassandra

If your Lagom service uses Cassandra for persistence then you use a pre-configured bundle to run Cassandra inside of ConductR.

First, load the Cassandra on to ConductR:

```console
> conduct load cassandra
```

To run the cassandra bundle execute:

```
> conduct run cassandra
```

If the Cassandra bundle has been started on ConductR after the Lagom service itself then it will take a couple of seconds until the Lagom service connects to Cassandra.

For convenience we recommend that you start with one Cassandra cluster per root sbt project, which of course can contain many Lagom projects (and therefore services). Bounded contexts are always maintained via separate key-spaces, and so having one Cassandra cluster is viable for supporting many microservices. The actual number of Cassandra clusters required will be the _Lagom amount_ i.e. "just the right amount" for your system. For more information on configuring Cassandra for ConductR please visit [the bundle's website](https://github.com/typesafehub/conductr-cassandra).
