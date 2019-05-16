# Set up Immutables in your IDE

[Immutables](https://immutables.github.io) is a library we recommend to create immutable objects and reduce boilerplate code to a minimum. We believe you will find this library handy, but because immutables generates sources from annotations, you need to enable the Java compiler annotation processor.

## Eclipse

To setup the Immutables annotation processor in Eclipse you need to configure the following for each project that is using the [Immutables](https://immutables.github.io) tool to generate [[immutable objects|Immutable]]. 

Open project Properties > Java Compiler > **Annotation Processing**

[[eclipse-immutables1.png]]

Enable project specific settings.

Enter `target/apt_generated` in the **souce directory** field.

Open project Properties > Java Compiler > Annotation Processing > **Factory Path**

Enable project specific settings.

Click **Add Variable...** 

The first time you should click **Configure Variables** and enter a new variable named `immutables` with the path `/<user home directory>/.ivy2/cache/org.immutables/value/jars/value-<version>.jar` (replace `<user home directory>` with your real home directory, which contains the `.ivy2` directory, and `<version>` with the version of immutables in use).

[[eclipse-immutables2.png]]

Select the `immutables` variable.

[[eclipse-immutables3.png]]

For next project you can simply select the `immutables` variable in the Factory Path dialog.

If you are importing many projects and you find the above configuration dialogs tedious you can do the changes for one project and then copy the following settings files to the other projects:

    .factorypath
    .settings/org.eclipse.jdt.apt.core.prefs
    .settings/org.eclipse.jdt.core.prefs

## IntelliJ IDEA

To setup the Immutables annotation processor in IntelliJ you need to follow [these instructions](https://immutables.github.io/apt.html#intellij-idea) (there is one caveat though, please make sure to read the next paragraph) for each project that is using the [Immutables](https://immutables.github.io) tool to generate [[immutable objects|Immutable]].

The one caveat is that you should set the "Production sources directory" to `target/scala-2.12/src_managed/main` (or `target/scala-2.13/src_managed/main` if using Scala 2.13) and the "Test source directory" to `target/src_managed/test`, instead of using the ones recommended in the linked instructions. The reason is that there are a couple of issues related to using immutables in IntelliJ with the IntelliJ sbt plugin (specifically, [SCL-8543](https://youtrack.jetbrains.com/issue/SCL-8543) and [IDEA-117540](  https://youtrack.jetbrains.com/issue/IDEA-117540)).
Finally, under "Project Structure" you should undo the exclusion of the `target`-directory and verify whether `target/scala-2.12/src_managed/main` (or `target/scala-2.13/src_managed/main` if using Scala 2.13)  is marked as 'Sources' for each module.
