# Creating an Eclipse Run Configuration

A **Run Configuration** allows you to run your new project in the **Eclipse Console** with one click. Follow these steps to create a **Run Configuration**:

1. From the Eclipse **Run** menu, select **Run Configurations**.
    The **Run Configurations** wizard opens.
    [[EclRunConfigurations.png]]
1. In the left pane, expand **Maven Build** and select **New_configuration**.
    The wizard provides a tabbed dialog to set up your configuration. Your screen will look similar to the following, depending on how your M2 plugin is configured. Configuration supplies some default values.
    [[EclNewRunConfiguration.png]]
1. Enter a name for the configuration.
1. On the **Main** tab:
  1. For the **Base directory** select the location of your top-level project. For example, if you created the project in your workspace and named it `my-first-system`, click  **Workspace** and select the **my-first-system** folder.
  1. In the **Goals:** field, enter `lagom:runAll`.
1. On the **JRE** tab, ensure that you are using a JRE from a supported JDK.
1. You might want to tune other settings later, but for now, click **Apply**.
1. Click **Run** to test the configuration.
    The build and startup will take a minute, the **Console** should display an informational message stating that the services are started.
    [[EclConsoleMaven.png]]
    
The **Run** button on the toolbar automatically uses the configuration you ran last, or you can select it from the menu. 
