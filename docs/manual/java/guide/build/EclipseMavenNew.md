# Creating a new Maven Eclipse project with the Lagom archetype

The Lagom Maven archetype allows you to quickly create a project for development. Follow these steps to:

* [Check Prerequisites](check-prerequisites)
* [Create your project](#create-a-project)
* [[Create an Eclipse Run Configuration](EclipseRunConfig)

## Check Prerequisites
Before attempting to create a Lagom Maven project in Eclipse, ensure that Eclipse is configured with the following:

* An [m2eclipse](http://www.eclipse.org/m2e/documentation/m2e-documentation.html) plugin compatible with Maven 3.3 or higher.
* A JDK 1.8 or higher.

## Create your project
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
    * **Version:** The Lagom version number, such as 1.3.0.
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

> **Note**  You can ignore warning decorations on project folders.
    
  