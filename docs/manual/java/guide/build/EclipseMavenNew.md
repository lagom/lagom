# Creating a new Maven Eclipse project with the Lagom archetype

The Lagom Maven archetype allows you to quickly create a project for development. Follow these steps to:

* [Check Prerequisites](#Check-prerequisites)
* [Create your project](#Create-an-Eclipse-project)

## Check prerequisites

Before attempting to create a Lagom Maven project in Eclipse, ensure that Eclipse is configured with the following:

* An [m2eclipse](https://www.eclipse.org/m2e/documentation/m2e-documentation.html) plugin compatible with Maven 3.3 or higher.
* A JDK 1.8

## Create an Eclipse project

The screen shots on this page reflect Eclipse Neon (4.6.2) with M2E (Maven Integration for Eclipse) version 1.7.0. If you are using different versions, screens might differ, but the procedures should be the same.

In Eclipse, follow these steps to create a project using the Lagom Maven archetype:

1. From the **File** menu, select **New > Project**.
    The **New Project** screen opens.
1. Expand **Maven**, select **Maven Project**, and click **Next**.
    The **New Maven project** wizard opens. [[EclNewMavenProj.png]]
1. Leave the default, **Use default Workspace location** box selected and click **Next**.
    The **Select an archetype** page opens.[[EclSelectArch.png]]
1. If the Lagom archetype appears in the list, select it. If not, click **Add Archetype** and supply the following values:
    * **Archetype Group Id:** com.lightbend.lagom
    * **Archetype Artifact Id:** maven-archetype-lagom-java
    * **Version:** The Lagom version number. Be sure to use the [current stable release](https://www.lagomframework.com/documentation/).
    * **Repository URL:** Leave blank
    [[EclAddArchetype.png]]

1. Click **OK**.
    The next page of the wizard opens, providing fields to identify the project and displaying the `hello` and `stream` properties from the archetype.

1. To identify your project, enter the following:
    * **Group Id**  - Usually a reversed domain name, such as `com.example.hello`.
    * **Artifact Id** - Maven also uses this value as the name for the top-level project folder. You might want to use a value such as `my-first-system`.
    * **Version** - A version number for your project.
    * **Package** - By default, the same as the `groupId`.

1. Click **Finish** and the projects created by the archetype display in the **Package Explorer**.

1. Run the project:
    1. Right-click the parent project folder.
    Eclipse puts all of the Maven project folders at the same level, so be sure to select the correct one. For example, if you used `my-first-system` as the Maven artifact ID, right-click `my-first-system`.
    1. Select **Run as ...** > **Maven Build**.
    1. In the **Goals** field, enter `lagom:runAll`.
    1. Select the **JRE** tab and make sure it is pointing at a JRE associated with a JDK.
    1. Click **Run**.

The console should report that the services started. Verify that the services are indeed up and running by invoking the `hello` service endpoint from any HTTP client, such as a browser:

```
http://localhost:9000/api/hello/World
```
The request returns the message `Hello, World!`.

