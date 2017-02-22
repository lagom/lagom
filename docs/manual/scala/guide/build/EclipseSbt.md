# Importing an sbt project into Eclipse

If you worked through the command line example for [[sbt|GettingStarted]], you have an sbt build. The sbt tool provides an [sbt-eclipse](https://github.com/typesafehub/sbteclipse) plugin that generates Eclipse project artifacts, which you can then import into Eclipse.

Follow these steps to integrate your project with Eclipse:

1. Configure sbt to include the Eclipse plugin:
    
    1. In the `project` folder of your sbt build, create an `eclipse.sbt` file. For example, with a project named `hello`, create a `hello/project/eclipse.sbt` file. 
    
    1. Add the following line to the file:
    ```
    addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.1.0")
    ```
    1. Save the file. 
    
1. In a console, `cd` to the top-level folder, and enter `sbt eclipse` to start the build tool and generate the eclipse project for all projects in your build.
    The output will look similar to the following:

    ```
    ...
    [info] Successfully created Eclipse project files for project(s):
    [info] hello-impl
    [info] hello-api
    [info] hello-stream-impl
    [info] hello-stream-api
    [info] lagom-internal-meta-project-service-locator
    [info] lagom-internal-meta-project-cassandra
    
    ```

1. Start Eclipse and switch to the Workspace you want to use for your Lagom project.

1. From the **File** menu, select **Import**.
   The **Select** screen opens. 

1. Expand **General**, select **Existing Projects into Workspace** and click **Next**.
   The **Import Projects** page opens.
   
1. Click **Browse**, select the top-level project folder, and click **OK**.
    The sub-projects display in the dialog. For example:
    [[EclBrowseToSbt.png]]
1. Click **Finish**.

Your project should be imported and ready to work with.