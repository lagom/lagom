# Importing an existing Maven project into IntelliJ

If you worked through the command line example for [[Maven|GettingStartedMaven]], you have a Maven project that you can integrate into Intellij IDEA Using built in [IntelliJ Maven](https://www.jetbrains.com/help/idea/2016.3/getting-started-with-maven.html) support. Before integrating your project, make sure that your IntelliJ **Settings** use the following:

* Maven 3.3 or higher
* A JDK 1.8

> **Note** When developing with Lagom you will often run several services in a single Java Virtual Machine. See how to [[increase Memory for Maven|JVMMemoryOnDev]].

To integrate an existing Maven Java project into IDEA, follow these steps:

1. Open IntelliJ IDEA and close any existing project.

1. From the `Welcome` screen, click **Import Project**.
    The `Select File or Directory to Import` dialog opens.

1. Navigate to your Maven project and select the top-level folder. For example, with a project named `my-first-system`:
    [[IDEASelectMavenFolder.png]]

1. Click **OK**.
    The **Import Project** screen opens:
    [[IDEAImportProject.png]]

1. For the **Import project from external model** value, select **Maven** and click **Next**.

1. Select **Import Maven projects automatically** and leave the other fields with default values.

1. Click **Next**.
    Your Maven project should be selected for import:
    [[IDEAProjSelected.png]]

1. Click **Next**.
    The **Please select project SDK** screen opens.

1. Make sure the correct JDC is selected and click **Next**.

1. Change the project name and location if you like, and click **Finish**.

1. Create a run configuration to test your project:

    1. From the **Run** menu, select **Run**.
    1. Click **Edit Configurations...**.
    1. Make sure **Maven** is selected, and click **+** (Add New Configuration).
    [[IDEANewMavenConfig.png]]
    1. Select **Maven**.
    1. Give your configuration a name.
    1. In the **Comand line:** field, enter `lagom:runAll`.
    1. Click **OK**.

1. Build your project and run it using the configuration you created.
    The console displays the following when Lagom is running:
    [[IDEASuccessMavenRun.png]]

1. Verify that the services are indeed up and running by invoking the `hello` service endpoint from any HTTP client, such as a browser:

    ```
    http://localhost:9000/api/hello/World
    ```
    The request returns the message `Hello, World!`.





