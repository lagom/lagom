# Import a Lagom build in your IDE

Here you will learn how to import projects defined in a Lagom build into your preferred IDE.

## Using Maven

If using Maven, use the m2clipse plugin in Eclipse, or the built in IntelliJ Maven support in IntelliJ, to import your project.

## Using sbt

### Eclipse

[sbt-eclipse](https://github.com/typesafehub/sbteclipse) is needed to generate the Eclipse project files, which are required to import all projects defined in your Lagom build into Eclipse.

If your Lagom build file is in directory `hello`, create a `project/eclipse.sbt` with the following content:

```
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "3.0.0")
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

One thing you'll notice after opening the imported projects in Eclipse is that a few unneeded source directories such as `src/main/scala` and `src/test/scala` have been created. It is possible to correct this behavior, but you will need to make a few changes in your Lagom build file. First, add the following definitions in your build (anywhere):

```
// All projects that you would like to import in Eclipse should use 
// this factory.
// Here is an usage example:
// {{{
//   lazy val userApi = project("user-api")
//     .settings(libraryDependencies += lagomJavadslApi)
//
//   lazy val userImpl = project("user-impl")
//     .settings(libraryDependencies += lagomJavadslServer)
//     .dependsOn(userApi)
// }}}
def project(id: String) = Project(id, base = file(id))
  .settings(eclipseSettings: _*)

// Configuration of sbteclipse
// Needed for importing the project into Eclipse
lazy val eclipseSettings = Seq(
  EclipseKeys.projectFlavor := EclipseProjectFlavor.Java,
  EclipseKeys.withBundledScalaContainers := false,
  EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource,
  EclipseKeys.eclipseOutput := Some(".target"),
  // will automatically download and attach sources if available
  EclipseKeys.withSource := true,
  // will automatically download and attach javadoc if available
  EclipseKeys.withJavadoc := true,
  // avoid some scala specific source directories
  unmanagedSourceDirectories in Compile := Seq((javaSource in Compile).value),
  unmanagedSourceDirectories in Test := Seq((javaSource in Test).value)
)
```

Now update all projects declarations in your Lagom build to use the newly created `project` method, so that the defined `eclipseSettings` are successfully applied to all projects.

Finally, you will need to regenerate the Eclipse project files to account for the changes made in the build file. Go back to the sbt console, type `reload` to reload the build file, and type `eclipse`. Once the `eclipse` task completes, go back to Eclipse, hit F5 to refresh all previously imported projects, and you are done.

### IntelliJ

Because the Lagom build file is written in sbt, we recommend to install the IntelliJ IDEA sbt plugin to facilitate importing projects. Unfortunately, for sbt plugin to properly work, you will need to install the Scala plugin as well. To do so, open the `Plugins` modal for installing plugins, search for the "SBT" plugin. If no match is found, you'll have to hit "Browse".

[[idea_search_sbt_plugin.png]]

Select and install the plugin.

[[idea_install_sbt_plugin.png]]

Repeat the same process for the "Scala" plugin.

After restarting IntelliJ IDEA, go to "Open...", and select your Lagom `build.sbt` file. An "Import Project from SBT" modal will open, and we suggest you to tick "Use auto-import", and also tick the option to download sources and javadocs:

[[idea_sbt_project_import.png]]

Click "OK" and continue by importing all projects.
