# Importing an sbt project into Eclipse

You might have used the Giter8 template to create a build as described in the [[Getting Started|GettingStartedSbt]] section, or you might have created one yourself. The sbt tool provides an [sbt-eclipse](https://github.com/typesafehub/sbteclipse) plugin that generates Eclipse project artifacts, which you can then import into Eclipse.

Follow these steps to integrate your project with Eclipse:
  
1. In a console, `cd` to the top-level sbt project folder, and enter `sbt eclipse` to generate the Eclipse project files for all projects in your build.
    The output will look something like the following:
    
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
   
1. Click **Browse**, select the top-level sbt project folder, and click **OK**.
    The sub-projects display in the dialog. For example:
    [[EclBrowseToSbt.png]]
1. Optionally, select **Copy projects into workspace**.
1. Click **Finish**.

Your project should be imported and ready to work with. Your project should be imported and ready to work with. As a reminder on how to run and test a project creatd with the template, see [[Creating and Running Hello World with sbt|GettingStartedSbt]].