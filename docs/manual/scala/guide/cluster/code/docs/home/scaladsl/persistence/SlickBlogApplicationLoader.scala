/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

import com.lightbend.lagom.scaladsl.server.LagomApplication
import com.lightbend.lagom.scaladsl.server.LagomApplicationContext
import com.lightbend.lagom.scaladsl.server.LagomServer
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.db.HikariCPComponents
import com.softwaremill.macwire._
import com.lightbend.lagom.scaladsl.persistence.jdbc._
import com.lightbend.lagom.scaladsl.persistence.slick._

//#load-components
abstract class SlickBlogApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with JdbcPersistenceComponents
    with SlickPersistenceComponents
    with HikariCPComponents
    with AhcWSComponents {
//#load-components

  // Bind the services that this server provides
  override lazy val lagomServer = ???

  lazy val myDatabase: MyDatabase = MyDatabase

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry = BlogPostSerializerRegistry

  // Register the Blog application persistent entity
  persistentEntityRegistry.register(wire[Post])

  // Register the event processor
  //#register-event-processor
  readSide.register(wire[BlogEventProcessor])
  //#register-event-processor
}
