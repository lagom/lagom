# Importing an sbt project into Eclipse

If you used the Giter8 template to create a build as described in [[Creating and running Hello World with sbt|IntroGetStarted]], you will have an sbt project. To make it possible to import the project into Eclipse, sbt provides an [sbt-eclipse](https://github.com/typesafehub/sbteclipse) plugin that generates Eclipse project artifacts for each of the subprojects.

Follow these steps to integrate your project with Eclipse:

1. [Import the project](#Import-the-project)
1. [Create an External Tool Configuration](#Create-an-External-Tool-Configuration)

# Import the project
  
1. In a console, `cd` to the top-level folder of your existing sbt project, and enter `sbt eclipse` to generate the Eclipse project files for all projects in your build.
    The sbt plugin creates `.project` and `.classpath` files for the subprojects. The last few lines of output confirm success:
    
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
    [[EclBrowseSbtScala.png]]
1. Optionally, select **Copy projects into workspace**.
1. Click **Finish**.

# Create an External Tool Configuration

1. From the Eclipse toolbar, click **External Tools** and select **External Tools Configurations**.
    [[EclExternalConfig.png]] 
1. In the left pane, select **Program** and click **New Launch Configuration**.
    [[EclNewLaunch.png]] 
1. Create your configuration as follows:
    1. Enter a name.
    1. On the **Main** tab in the **Location** field, enter the location of your sbt installation.
    For Windows, browse to the location where you installed sbt and select `sbt.bat`. On Linux, you can find the location of sbt by opening a terminal and entering `which sbt`.
    1. For **Working Directory** browse to the top-level sbt project folder. (This is the folder containing the `build.sbt` file.)
    1. In the **Arguments** field, enter `runAll`.
    1. Click **Apply**. Your screen should look similar to the following:
    [[EclExtConfigScala.png]] 
1. Click **Run**.
    On success, the console shows that the services are running.
    [[EclSbtScalaSuccess.png]]
    
    Verify that the services are indeed up and running by invoking the `hello` service endpoint from any HTTP client, such as a browser: 
        
        ```
        http://localhost:9000/api/hello/World
        ```
    The request returns the message `Hello, World!`.
    
    
