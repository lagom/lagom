# Increase Memory for sbt and Maven

Lagom in dev mode starts all your services plus some internal Lagom services in a single JVM. This can cause `OutOfMemoryError`s depending on your JVM setup. It is possible to start Maven and sbt with increased memory.

We recommend you increase the Maximum Metaspace size and the Thread Stack size. These values can be set using `-Xss2M -XX:MaxMetaspaceSize=1024M`. The exact values may depend on your hardware and your code base.


## Maven

You can start Maven with extra memory using `MAVEN_OPTS` environment variable.

```bash
$ MAVEN_OPTS="-Xms512M -Xmx1024M -Xss2M -XX:MaxMetaspaceSize=1024M" mvn lagom:runAll
```

In this example we're setting an initial JVM Heap of 512Mb, a maximum Heap of 1024M, a thread stack of 2M and maximum Metaspace size of 1024M.

Stating the `MAVEN_OPTS` on every invocation is error prone and exporting it globally might not be possible if you need different settings for different projects. You may want to use [direnv](https://direnv.net/) to setup the environment variables per project.

## sbt

You can start sbt with extra memory using `SBT_OPTS` environment variable.

```bash
$ SBT_OPTS="-Xms512M -Xmx1024M -Xss2M -XX:MaxMetaspaceSize=1024M" sbt
```

sbt lets you list the JVM options you need to run your project on a file named `.jvmopts` in the root of your project.

```
$ cat .jvmopts
-Xms512M
-Xmx4096M
-Xss2M
-XX:MaxMetaspaceSize=1024M
```

`sbt` also supports other means to tune the underlying JVM. See `sbt -h` for more details.
