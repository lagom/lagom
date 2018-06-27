# Importing an existing Maven project into Eclipse

If you have a Maven project created from an archetype (as described in [[Creating and running Hello World with Maven|GettingStartedMaven]]) or one you created on your own, import it into Eclipse as follows:

* [Check Prerequisites](#Check-prerequisites)
* [Import the project](#Import-the-project)

## Check prerequisites

Before attempting to create a Lagom Maven project in Eclipse, ensure that Eclipse is configured with the following:

* An [m2eclipse](https://www.eclipse.org/m2e/documentation/m2e-documentation.html) plugin compatible with Maven 3.3 or higher.
* A JDK 1.8

# Import the project

1. From the **File** menu, select **Import**.
   The **Select** screen opens.
1. Expand **Maven** and select **Existing Maven Projects**.
    [[EclSelectImportType.png]]
1. Click **Next**.
1. For **Root Directory**, click **Browse** and select the top-level project folder.
    [[EclBrowseMvnImp.png]]
1. Verify that the **Projects** list includes all subprojects and click **Finish**.
1. Run the project:
    1. Right-click the parent project folder.
    Eclipse puts all of the Maven project folders at the same level, so be sure to select the correct one. For example, if you used `my-first-system` as the Maven artifact ID, right-click `my-first-system`.
    1. Optionally, change the name.
    1. Select **Run as ...** > **Maven Build**.
    1. In the **Goals** field, enter `lagom:runAll`.
    1. Select the **JRE** tab and make sure it is pointing at a JRE associated with a JDK.
    1. Click **Run**.


The console should report that the services started. Verify that the services are indeed up and running by invoking the `hello` service endpoint from any HTTP client, such as a browser:

    ```
    http://localhost:9000/api/hello/World
    ```
The request returns the message `Hello, World!`.



