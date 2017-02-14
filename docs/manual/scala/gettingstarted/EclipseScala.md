# Integrating sbt builds with Eclipse

If you worked through the command line example for [[sbt|GettingStarted]], you have an sbt build. This page describes how to integrate with Eclipse.

Before integrating a Lagom sbt build with Eclipse, you must download and install [sbt-eclipse](https://github.com/typesafehub/sbteclipse). This plugin provides the support to generate Eclipse project files, which are required to import Lagom builds into Eclipse.

If your Lagom build file is in directory `hello`, create a `project/eclipse.sbt` with the following content:

```
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.1.0")
```

Save the file. Now, open the terminal, and `cd` to the `hello` directory, and type `sbt`:

```
$ cd hello
$ sbt
... (booting up)
>
```

If you have configured things correctly, typing `eclipse` will generate the Eclipse project files for all projects in your build:

```
> eclipse
...
[info] Successfully created Eclipse project files for project(s):
[info] hello-impl
[info] hello-api
[info] hello-stream-impl
[info] hello-stream-api
[info] lagom-internal-meta-project-service-locator
[info] lagom-internal-meta-project-cassandra
>
```

Open Eclipse and follow the [Eclipse instructions](http://help.eclipse.org/mars/index.jsp?topic=%2Forg.eclipse.platform.doc.user%2Ftasks%2Ftasks-importproject.htm) for importing existing projects. Also, mind that `lagom-internal-meta-project-service-locator` and `lagom-internal-meta-project-cassandra` are internal projects that you don't need to import, so just unselect them:

[[eclipse_import_unselect_synthetic_projects.png]]
