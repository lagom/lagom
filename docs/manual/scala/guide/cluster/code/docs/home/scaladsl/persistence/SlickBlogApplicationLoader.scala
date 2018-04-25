/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

//#load-components
abstract class BlogApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with JdbcPersistenceComponents
    with SlickPersistenceComponents
    with HikariCPComponents
    with AhcWSComponents {
//#load-components

  // Bind the services that this server provides
  override lazy val lagomServer = ???

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry = BlogPostSerializerRegistry

  // Register the Blog application persistent entity
  persistentEntityRegistry.register(wire[Post])

  // Register the event processor
  //#register-event-processor
  readSide.register(wire[BlogEventProcessor])
  //#register-event-processor
}