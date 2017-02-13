# Integrating sbt builds with IntelliJ

Because the Lagom build file is written in sbt, we recommend installing the IntelliJ IDEA sbt plugin to facilitate importing projects. (The sbt plugin has dependencies on the Scala plugin, so you will have to install it as well). 

To do so, open the `Plugins` modal for installing plugins, search for the "SBT" plugin. If no match is found, you'll have to hit "Browse".

[[idea_search_sbt_plugin.png]]

Select and install the plugin.

[[idea_install_sbt_plugin.png]]

Repeat the same process for the "Scala" plugin.

After restarting IntelliJ IDEA, go to "Open...", and select your Lagom `build.sbt` file. An "Import Project from SBT" modal will open, and we suggest you to tick "Use auto-import", and also tick the option to download sources and javadocs:

[[idea_sbt_project_import.png]]

Click "OK" and continue by importing all projects.
